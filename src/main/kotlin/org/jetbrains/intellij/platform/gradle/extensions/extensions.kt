// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("MayBeConstant", "ObjectPropertyName")

package org.jetbrains.intellij.platform.gradle.extensions

import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XML
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.gradle.api.file.Directory
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.IvyModule
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Creates an Ivy dependency XML file for an external module.
 *
 * @param localPlatformArtifactsPath The [Path] to the local IntelliJ Platform artifacts directory.
 * @param publications The list of [IvyModule.Publication] objects to be included in the Ivy file.
 */
internal fun ExternalModuleDependency.createIvyDependencyFile(
    localPlatformArtifactsPath: Path,
    publications: List<IvyModule.Publication>,
    dependencies: List<IvyModule.Dependency> = emptyList(),
) =
    createIvyDependencyFile(
        group = group,
        name = name,
        version = version.orEmpty(),
        localPlatformArtifactsPath = localPlatformArtifactsPath,
        publications = publications,
        dependencies = dependencies,
    )

/**
 * Creates an Ivy dependency XML file for an external module.
 *
 * @param localPlatformArtifactsPath The [Path] to the local IntelliJ Platform artifacts directory.
 * @param publications The list of [IvyModule.Publication] objects to be included in the Ivy file.
 */
internal fun createIvyDependencyFile(
    group: String?,
    name: String,
    version: String,
    localPlatformArtifactsPath: Path,
    publications: List<IvyModule.Publication>,
    dependencies: List<IvyModule.Dependency> = emptyList(),
) {
    val ivyFile = localPlatformArtifactsPath
        .resolve("$group-$name-$version.xml")
        .takeUnless { it.exists() }
        ?: return

    val ivyModule = IvyModule(
        info = IvyModule.Info(
            organisation = group,
            module = name,
            revision = version,
        ),
        publications = publications,
        dependencies = dependencies
    )

    with(ivyFile) {
        parent.createDirectories()
        createFile()
        writeText(XML {
            indentString = "  "
        }.encodeToString(ivyModule))
    }
}

/**
 * Resolves the artifact path for the given [localPath] as it may accept different data types.
 *
 * @param localPath The local path of the artifact. Accepts either [String], [File], or [Directory].
 * @return The resolved artifact path as a Path object.
 * @throws IllegalArgumentException if the [localPath] is not of supported types.
 */
@Throws(IllegalArgumentException::class)
internal fun resolveArtifactPath(localPath: Any) = when (localPath) {
    is String -> localPath
    is File -> localPath.absolutePath
    is Directory -> localPath.asPath.pathString
    else -> throw IllegalArgumentException("Invalid argument type: '${localPath.javaClass}'. Supported types: String, File, or Directory")
}
    .let { Path(it) }
    .let { it.takeUnless { OperatingSystem.current().isMacOsX && it.extension == "app" } ?: it.resolve("Contents") }
    .takeIf { it.exists() && it.isDirectory() }
    .let { requireNotNull(it) { "Specified localPath '$localPath' doesn't exist or is not a directory" } }

/**
 * Returns the Gradle project cache directory.
 */
internal fun ProviderFactory.intellijPlatformCachePath(rootProjectDirectory: Path) =
    gradleProperty(GradleProperties.INTELLIJ_PLATFORM_CACHE).orNull
        .takeUnless { it.isNullOrBlank() }
        ?.let { Path(it) }
        .run { this ?: rootProjectDirectory.resolve(CACHE_DIRECTORY) }
        .createDirectories()
        .absolute()

/**
 * Represents the local platform artifacts directory path which contains Ivy XML files.
 *
 * @see [createIvyDependencyFile]
 * @see [IntelliJPlatformRepositoriesExtension.localPlatformArtifacts]
 * @see [GradleProperties.LOCAL_PLATFORM_ARTIFACTS]
 */
internal fun ProviderFactory.localPlatformArtifactsPath(rootProjectDirectory: Path) =
    gradleProperty(GradleProperties.LOCAL_PLATFORM_ARTIFACTS).orNull
        .takeUnless { it.isNullOrBlank() }
        ?.let { Path(it) }
        .run { this ?: intellijPlatformCachePath(rootProjectDirectory).resolve("localPlatformArtifacts") }
        .createDirectories()
        .absolute()

/**
 * Retrieves URLs from registered repositories.
 */
internal fun RepositoryHandler.urls() = mapNotNull { (it as? UrlArtifactRepository)?.url?.toString() }
internal fun String.parseIdeNotation() = split('-').let {
    when {
        it.size == 2 -> it.first().toIntelliJPlatformType() to it.last()
        else -> IntelliJPlatformType.IntellijIdeaCommunity to it.first()
    }
}
/**
 * Type alias for a lambda function that takes a [Dependency] and performs some actions on it.
 */
internal typealias DependencyAction = (Dependency.() -> Unit)

/**
 * Parses the plugin notation into the `<id, version, channel` triple.
 *
 * Possible notations are `id:version` or `id:version@channel`.
 */
internal fun String.parsePluginNotation() = trim()
    .takeIf { it.isNotEmpty() }
    ?.split(":", "@")
    ?.run { Triple(getOrNull(0).orEmpty(), getOrNull(1).orEmpty(), getOrNull(2).orEmpty()) }

// TODO: cleanup JBR helper functions:
internal fun from(jbrVersion: String, jbrVariant: String?, jbrArch: String?, operatingSystem: OperatingSystem = OperatingSystem.current()): String {
    val version = "8".takeIf { jbrVersion.startsWith('u') }.orEmpty() + jbrVersion
    var prefix = getPrefix(version, jbrVariant)
    val lastIndexOfB = version.lastIndexOf('b')
    val lastIndexOfDash = version.lastIndexOf('-') + 1
    val majorVersion = when (lastIndexOfB > -1) {
        true -> version.substring(lastIndexOfDash, lastIndexOfB)
        false -> version.substring(lastIndexOfDash)
    }
    val buildNumberString = when (lastIndexOfB > -1) {
        (lastIndexOfDash == lastIndexOfB) -> version.substring(0, lastIndexOfDash - 1)
        true -> version.substring(lastIndexOfB + 1)
        else -> ""
    }
    val buildNumber = buildNumberString.toVersion()
    val isJava8 = majorVersion.startsWith("8")
    val isJava17 = majorVersion.startsWith("17")
    val isJava21 = majorVersion.startsWith("21")

    val oldFormat = prefix == "jbrex" || isJava8 && buildNumber < "1483.24".toVersion()
    if (oldFormat) {
        return "jbrex${majorVersion}b${buildNumberString}_${platform(operatingSystem)}_${arch(false)}"
    }

    val arch = jbrArch ?: arch(isJava8)
    if (prefix.isEmpty()) {
        prefix = when {
            isJava17 || isJava21 -> "jbr_jcef-"
            isJava8 -> "jbrx-"
            operatingSystem.isMacOsX && arch == "aarch64" -> "jbr_jcef-"
            buildNumber < "1319.6".toVersion() -> "jbr-"
            else -> "jbr_jcef-"
        }
    }

    return "$prefix$majorVersion-${platform(operatingSystem)}-$arch-b$buildNumberString"
}

private fun getPrefix(version: String, variant: String?) = when {
    !variant.isNullOrEmpty() -> when (variant) {
        "sdk" -> "jbrsdk-"
        else -> "jbr_$variant-"
    }

    version.startsWith("jbrsdk-") -> "jbrsdk-"
    version.startsWith("jbr_jcef-") -> "jbr_jcef-"
    version.startsWith("jbr_dcevm-") -> "jbr_dcevm-"
    version.startsWith("jbr_fd-") -> "jbr_fd-"
    version.startsWith("jbr_nomod-") -> "jbr_nomod-"
    version.startsWith("jbr-") -> "jbr-"
    version.startsWith("jbrx-") -> "jbrx-"
    version.startsWith("jbrex8") -> "jbrex"
    else -> ""
}

private fun platform(operatingSystem: OperatingSystem) = when {
    operatingSystem.isWindows -> "windows"
    operatingSystem.isMacOsX -> "osx"
    else -> "linux"
}

private fun arch(newFormat: Boolean): String {
    val arch = System.getProperty("os.arch")
    if ("aarch64" == arch || "arm64" == arch) {
        return "aarch64"
    }
    if ("x86_64" == arch || "amd64" == arch) {
        return "x64"
    }
    val name = System.getProperty("os.name")
    if (name.contains("Windows") && System.getenv("ProgramFiles(x86)") != null) {
        return "x64"
    }
    return when (newFormat) {
        true -> "i586"
        false -> "x86"
    }
}
