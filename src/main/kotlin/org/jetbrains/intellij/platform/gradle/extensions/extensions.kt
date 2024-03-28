// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XML
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.file.Directory
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.BuildException
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.GradleProperties
import org.jetbrains.intellij.platform.gradle.models.IvyModule
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.throwIfNull
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.*

/**
 * Creates an Ivy dependency XML file for an external module.
 *
 * @param localPlatformArtifactsPath The [Path] to the local IntelliJ Platform artifacts directory.
 * @param publications The list of [IvyModule.Publication] objects to be included in the Ivy file.
 */
internal fun ExternalModuleDependency.createIvyDependency(localPlatformArtifactsPath: Path, publications: List<IvyModule.Publication>) {
    val ivyFile = localPlatformArtifactsPath
        .resolve("$group-$name-$version.xml")
        .takeUnless { it.exists() }
        ?: return

    val ivyModule = IvyModule(
        info = IvyModule.Info(
            organisation = group,
            module = name,
            revision = version,
            publication = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
        ),
        configurations = listOf(
            IvyModule.Configuration(
                name = "default",
                visibility = "public",
            ),
        ),
        publications = publications,
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
 * @throws BuildException if the resolved path doesn't exist or is not a directory.
 */
@Throws(IllegalArgumentException::class, BuildException::class)
internal fun resolveArtifactPath(localPath: Any) = when (localPath) {
    is String -> localPath
    is File -> localPath.absolutePath
    is Directory -> localPath.asPath.absolutePathString()
    else -> throw IllegalArgumentException("Invalid argument type: '${localPath.javaClass}'. Supported types: String, File, or Directory")
}
    .let { Path(it) }
    .let { it.takeUnless { OperatingSystem.current().isMacOsX && it.extension == "app" } ?: it.resolve("Contents") }
    .takeIf { it.exists() && it.isDirectory() }
    .throwIfNull { BuildException("Specified localPath '$localPath' doesn't exist or is not a directory") }

/**
 * Returns the Gradle project cache directory.
 */
internal fun ProviderFactory.intellijPlatformCachePath(rootProjectDirectory: Path) =
    gradleProperty(GradleProperties.INTELLIJ_PLATFORM_CACHE).orNull
        .takeUnless { it.isNullOrBlank() }
        ?.let { Path(it) }
        ?: rootProjectDirectory.resolve(CACHE_DIRECTORY)

/**
 * Represents the local platform artifacts directory path which contains Ivy XML files.
 *
 * @see [createIvyDependency]
 * @see [IntelliJPlatformRepositoriesExtension.localPlatformArtifacts]
 * @see [GradleProperties.LOCAL_PLATFORM_ARTIFACTS]
 */
internal fun ProviderFactory.localPlatformArtifactsPath(rootProjectDirectory: Path) =
    gradleProperty(GradleProperties.LOCAL_PLATFORM_ARTIFACTS).orNull
        .takeUnless { it.isNullOrBlank() }
        ?.let { Path(it) }
        ?: intellijPlatformCachePath(rootProjectDirectory)
            .resolve("localPlatformArtifacts")
