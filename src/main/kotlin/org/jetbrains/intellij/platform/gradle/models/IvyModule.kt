// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.artifacts.transform.CollectorTransformer
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesHelper
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesHelper
import org.jetbrains.kotlin.util.removeSuffixIfPresent
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory

@Serializable
@XmlSerialName("ivy-module")
data class IvyModule(
    val version: String = "2.0",
    @XmlElement @XmlSerialName("info") val info: Info? = null,
    @XmlElement @XmlChildrenName("conf") val configurations: List<Configuration> = listOf(Configuration("default")),
    @XmlElement @XmlChildrenName("artifact") val publications: List<Artifact> = emptyList(),
    @XmlElement @XmlChildrenName("dependency") val dependencies: List<Dependency> = emptyList(),
) {

    @Serializable
    data class Configuration(
        val name: String? = null,
        val visibility: String = "public",
    )

    @Serializable
    data class Info(
        val organisation: String? = null,
        val module: String? = null,
        val revision: String? = null,
        val publication: String? = null,
    )

    @Serializable
    data class Artifact(
        val name: String? = null,
        val type: String? = null,
        val ext: String? = null,
        val conf: String? = "default",
        // Does not seem to be supported by Gradle:
        // https://ant.apache.org/ivy/history/2.4.0/ivyfile/artifact.html
        // https://docs.gradle.org/current/javadoc/org/gradle/api/publish/ivy/IvyArtifact.html
        val url: String? = null,
        val packaging: String? = null,
    )

    @Serializable
    data class Dependency(
        @XmlSerialName("org") val organization: String? = null,
        @XmlSerialName("name") val name: String,
        @XmlSerialName("rev") val version: String,
        @XmlElement @XmlSerialName("artifact") val artifacts: List<Artifact> = emptyList(),
    ) {

        @Serializable
        data class Artifact(
            val name: String,
            val type: String,
            val classifier: String? = null,
            @XmlSerialName("ext") val extension: String,
        )
    }
}

/**
 * Create a publication artifact element for Ivy XML file, like:
 *
 * ```XML
 * <ivy-module version="2.0">
 *     ...
 *     <publications>
 *         <artifact conf="default" ext="jar" name="absolute/path/to/artifact.jar" type="jar" />
 *     </publications>
 * </ivy-module>
 * ```
 * Or
 * ```XML
 * <ivy-module version="2.0">
 *     ...
 *     <publications>
 *         <artifact conf="default" ext="directory" name="absolute/path/to/jdk" type="directory" />
 *     </publications>
 * </ivy-module>
 * ```
 *
 * The artifact name is an actual path of the file or directory, later used to locate all entries belonging to the current local dependency.
 * Note that the path is built using [invariantSeparatorsPathString] due to path separator differences on Windows.
 *
 * In addition, the leading drive letter is always removed to start paths with `/` and to solve an issue with the Gradle file resolver.
 * If the artifact path doesn't start with `/`, the [BaseDirFileResolver] creates a malformed location with a Gradle base dir prepended,
 * like: `C:/Users/hsz/dir/C:/Users/hsz/path/to/artifact.jar`, so we make it `/Users/hsz/path/to/artifact.jar` explicitly.
 *
 * As we remove the drive letter, we later have to guess which drive the artifact belongs to by iterating over A:/, B:/, C:/, ...
 * but that's not a huge problem.
 *
 * @see IntelliJPlatformRepositoriesExtension.jetbrainsIdeInstallers
 * @see IntelliJPlatformRepositoriesHelper.createIvyArtifactRepository
 */
internal fun Path.toAbsolutePathIvyArtifact(): IvyModule.Artifact {
    // The contract is that we're working with absolute normalized paths here.
    val absNormalizedPath = this.absolute().normalize()

    val optionalExtString = when {
        // This value for some reason is very important; without it, artifacts pointing to dirs don't work.
        absNormalizedPath.isDirectory() -> "directory"
        else -> absNormalizedPath.extension
    }

    // Remove the leading "/" or drive letter for Windows, if present, because the artifact pattern adds it.
    val absPathStringWithoutLeading = absNormalizedPath.invariantSeparatorsPathString.removeLeadingPathSeparator()
    return IvyModule.Artifact(
        type = "",
        name = absPathStringWithoutLeading,
        ext = optionalExtString,
    )
}

/**
 * @see IntelliJPlatformDependenciesHelper.registerIntellijPlatformIvyRepo
 */
internal fun Path.toBundledIvyArtifactsRelativeTo(basePath: Path) = explodeIntoIvyJarsArtifactsRelativeTo(basePath)

/**
 * For local plugins, we don't use relative paths.
 * Since a base path is null, the result will be an absolute path.
 *
 * @see IntelliJPlatformRepositoriesHelper.createLocalIvyRepository
 */
internal fun Path.toAbsolutePathLocalPluginIvyArtifacts() = explodeIntoIvyJarsArtifactsRelativeTo(null)

/**
 * @see IntelliJPlatformRepositoriesHelper.createIvyArtifactRepository
 */
private fun Path.explodeIntoIvyJarsArtifactsRelativeTo(basePath: Path? = null): List<IvyModule.Artifact> {
    // The contract is that we're working with absolute normalized paths here.
    val absNormalizedPath = this.absolute().normalize()
    val absNormalizedBasePath = basePath?.absolute()?.normalize()

    // Never ever return an Artifact pointing to a directory from this method.
    val jars = when {
        absNormalizedPath.isDirectory() -> CollectorTransformer.collectJars(absNormalizedPath)
        else -> listOf(absNormalizedPath)
    }

    val absoluteNormalizedPaths = jars.map { it.absolute().normalize() }
    return absoluteNormalizedPaths.map { it.toArtifactRelativeTo(absNormalizedBasePath) }
}

private fun Path.toArtifactRelativeTo(basePath: Path?): IvyModule.Artifact {
    // The contract is that we're working with absolute normalized paths here.
    val absNormalizedPath = this.absolute().normalize()
    val absNormalizedBasePath = basePath?.absolute()?.normalize()

    val extString = absNormalizedPath.extension
    val absPathStringWithoutLeading = absNormalizedPath.containingDirPathStringRelativeTo(absNormalizedBasePath)
    // Remove the extension, if present, because the artifact pattern adds it.
    val fileNameWithoutExt = absNormalizedPath.fileName.toString().removeSuffixIfPresent(".$extString")

    return IvyModule.Artifact(
        type = absPathStringWithoutLeading,
        name = fileNameWithoutExt,
        ext = extString,
    )
}

/**
 * Returns an absolute, normalized, and invariant path string of the contenting directory.
 * The path will not have a leading path separator or drive letter for windows.
 *
 * Also, if the optional basePath is given, the path will be relative to it.
 * In this case, it also doesn't have the leading path separator.
 *
 * @see IntelliJPlatformRepositoriesHelper.createIvyArtifactRepository
 */
private fun Path.containingDirPathStringRelativeTo(basePath: Path? = null): String {
    // The contract is that we're working with absolute normalized paths here.
    val absNormalizedPath = this.absolute().normalize()
    val absNormalizedBasePath = basePath?.absolute()?.normalize()

    val absNormalizedPathStringWithoutLeading = when (absNormalizedBasePath) {
        // Remove the leading "/" or drive letter for Windows, if present, because the artifact pattern adds it.
        null -> absNormalizedPath.invariantSeparatorsPathString.removeLeadingPathSeparator()

        else -> {
            /**
             * Since the two paths must point to the same location, the validation is possible, and if not, that is a bug, and the artifact won't be found,
             * because the repository's artifact pattern will an absolute path build into it.
             * They shouldn't match only by a chance because we've removed drive letters for Windows.
             */
            if (!absNormalizedPath.normalize().startsWith(absNormalizedBasePath.normalize())) {
                throw IllegalStateException("The path '${absNormalizedPath}' is supposed to start with '${absNormalizedBasePath.normalize()}' .")
            }

            /**
             * If a base path is given, we make the returned path relative to it.
             * In this case, the drive letter on Windows gets removed automatically.
             * Also, there should be no leading "/", but to be safe, remove it too.
             */
            absNormalizedBasePath.relativize(absNormalizedPath).invariantSeparatorsPathString.removePrefix("/")
        }
    }

    // Removes the filename or the first directory if this is a directory.
    return absNormalizedPathStringWithoutLeading.substringBeforeLast("/")
}

/**
 * The contract is that we're working with absolute normalized paths here.
 */
private fun String.removeLeadingPathSeparator() = when {
    OperatingSystem.current().isWindows -> replaceFirst(Regex("^[a-zA-Z]:/"), "")
    else -> removePrefix("/")
}
