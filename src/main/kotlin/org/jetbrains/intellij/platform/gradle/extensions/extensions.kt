// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.file.Directory
import org.gradle.api.invocation.Gradle
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.BuildException
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.GradleProperties
import org.jetbrains.intellij.platform.gradle.model.IvyModule
import org.jetbrains.intellij.platform.gradle.model.XmlExtractor
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.throwIfNull
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.*

/**
 * Represents an annotation to mark DSL elements related to the IntelliJ Platform.
 */
@DslMarker
annotation class IntelliJPlatform

/**
 * Creates an Ivy dependency XML file for an external module.
 *
 * @param gradle The [Gradle] instance.
 * @param publications The list of [IvyModule.Publication] objects to be included in the Ivy file.
 */
internal fun ExternalModuleDependency.createIvyDependency(gradle: Gradle, publications: List<IvyModule.Publication>) {
    val ivyFile = gradle.localPlatformArtifacts
        .resolve("$group-$name-$version.xml")
        .takeUnless { it.exists() }
        ?: return

    val extractor = XmlExtractor<IvyModule>()
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

    ivyFile.parent.createDirectories()
    ivyFile.createFile()
    extractor.marshal(ivyModule, ivyFile)
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
internal val Gradle.intellijPlatformCache
    get() = rootProject
        .findProperty(GradleProperties.INTELLIJ_PLATFORM_CACHE)
        ?.toString()
        .takeUnless { it.isNullOrEmpty() }
        ?.let { Path(it) }
        ?: rootProject
            .projectDir
            .toPath()
            .resolve(CACHE_DIRECTORY)

/**
 * Represents the local platform artifacts directory path which contains Ivy XML files.
 *
 * @see [createIvyDependency]
 * @see [IntelliJPlatformRepositoriesExtension.localPlatformArtifacts]
 * @see [GradleProperties.LOCAL_PLATFORM_ARTIFACTS]
 */
internal val Gradle.localPlatformArtifacts
    get() = rootProject
        .findProperty(GradleProperties.LOCAL_PLATFORM_ARTIFACTS)
        ?.toString()
        .takeUnless { it.isNullOrEmpty() }
        ?.let { Path(it) }
        ?: intellijPlatformCache
            .resolve("localPlatformArtifacts")
