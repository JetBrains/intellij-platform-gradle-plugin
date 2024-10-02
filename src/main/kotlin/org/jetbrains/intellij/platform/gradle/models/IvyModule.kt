// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.jetbrains.intellij.platform.gradle.artifacts.transform.CollectorTransformer
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import org.jetbrains.kotlin.util.suffixIfNot
import java.nio.file.Path
import kotlin.io.path.*

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
 *         <artifact conf="default" ext="jar" name="/path/to/artifact.jar" type="jar" />
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
 */
internal fun Path.toIvyArtifact(): IvyModule.Artifact {
    // In this case name is the full path, so type is not needed.
    val type = ""
    // This is not a mistake.
    // Name is the full path in this case, so we have to treat is as we type in other cases.
    // In this case it is should be ok to have an absolute path in the name, because this is a fallback method, where
    // we do not care or do not know what kind of path we are dealing with, so we are leaving it as it.
    val name = normalizeTypePath(this)
    val ext = when {
        isDirectory() -> "directory"
        else -> extension
    }

    return IvyModule.Artifact(type = type, name = name, ext = ext)
}

internal fun Path.toBundledPluginIvyArtifacts(): List<IvyModule.Artifact> {
    val paths = this.resolve("lib")
    val jars: List<Path> = CollectorTransformer.collectJars(paths)

    return jars.map { it: Path ->
        // E.g.
        // <artifact name="indexing-shared.jar"
        //  type="home/user/.gradle/caches/8.10.2/transforms/52e973803d9e58157316ab0aa2c089e4/transformed/ideaIU-2023.3.8/plugins/indexing-shared/lib/"
        //  ext="jar"
        //  conf="default"
        // />
        // The reason why we put tha absolute path into type is that the name should not have it, because artifact name
        // may come up in files like Gradle's verification-metadata.xml
        // https://docs.gradle.org/current/userguide/dependency_verification.html
        // Which will make them not portable between different environments.
        val type = normalizeTypePath(it).substringBeforeLast("/").suffixIfNot("/")
        val name = normalizeNamePath(it)
        val ext = it.extension

        IvyModule.Artifact(type = type, name = name, ext = ext)
    }
}

internal fun Path.toBundledModuleIvyArtifacts(): List<IvyModule.Artifact> {
    val paths = this.resolve("lib/modules")
    val jars: List<Path> = CollectorTransformer.collectJars(paths)

    return jars.map { it: Path ->
        val type = normalizeTypePath(it).substringBeforeLast("/").suffixIfNot("/")
        val name = normalizeNamePath(it)
        val ext = it.extension

        IvyModule.Artifact(type = type, name = name, ext = ext)
    }
}

internal fun Path.toLocalPluginIvyArtifacts(): List<IvyModule.Artifact> {
    val paths = this.resolve("lib")
    val jars: List<Path> = CollectorTransformer.collectJars(paths)

    return jars.map { it: Path ->
        val type = normalizeTypePath(it).substringBeforeLast("/").suffixIfNot("/")
        val name = normalizeNamePath(it)
        val ext = it.extension

        IvyModule.Artifact(type = type, name = name, ext = ext)
    }
}

private fun normalizeNamePath(path: Path): String {
    return path.normalizeWindowsPath().substringAfterLast("/")
}

private fun normalizeTypePath(path: Path): String {
    // See why we need removePrefix("/") in:
    // IntelliJPlatformRepositoriesHelper.createLocalIvyRepository$IntelliJPlatformGradlePlugin
    // the pattern has to have that slash artifactPattern("/$pattern")
    return path.normalizeWindowsPath().removePrefix("/")
}

private fun Path.normalizeWindowsPath(): String {
    return invariantSeparatorsPathString.replaceFirst(Regex("^[a-zA-Z]:/"), "/")
}
