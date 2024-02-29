// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.JETBRAINS_RUNTIME_VENDOR
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.or
import org.jetbrains.intellij.platform.gradle.utils.throwIfNull
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Resolves Java Runtime.
 *
 * @param jetbrainsRuntime The [Configurations.JETBRAINS_RUNTIME] configuration.
 * @param intellijPlatform The [Configurations.INTELLIJ_PLATFORM] configuration.
 * @param javaToolchainSpec The Java Toolchain configured with [JavaPluginExtension].
 * @param javaToolchainService The [JavaToolchainService] used for finding a matching launcher.
 */
class JavaRuntimePathResolver(
    val jetbrainsRuntime: FileCollection,
    val intellijPlatform: FileCollection,
    val javaToolchainSpec: JavaToolchainSpec,
    val javaToolchainService: JavaToolchainService,
) : PathResolver(
    subject = "Java Runtime",
) {

    override val predictions: Sequence<Pair<String, () -> Path?>>
        get() = sequenceOf(
            /**
             * The exact JetBrains Runtime archive provided to the [Configurations.JETBRAINS_RUNTIME] configuration using
             * the [IntelliJPlatformDependenciesExtension.jetbrainsRuntime] dependencies extensions.
             */
            "JetBrains Runtime specified with dependencies" to {
                jetbrainsRuntime.singleOrNull()
                    ?.toPath()
                    .resolveRuntimeDirectory()
                    .ensureExecutableExists()
            },
            /**
             * Java Toolchain if the toolchain vendor matches [JETBRAINS_RUNTIME_VENDOR].
             */
            "JetBrains Runtime specified with Java Toolchain" to {
                @Suppress("UnstableApiUsage")
                javaToolchainSpec.vendor.orNull
                    .takeUnless { it == DefaultJvmVendorSpec.any() }
                    ?.takeIf { it.matches(JETBRAINS_RUNTIME_VENDOR) }
                    ?.let { javaToolchainService.launcherFor(javaToolchainSpec).get() }
                    ?.metadata
                    ?.installationPath
                    ?.asPath
                    .resolveRuntimeDirectory()
                    .ensureExecutableExists()
            },
            /**
             * The bundled JetBrains Runtime within the current IntelliJ Platform.
             */
            "JetBrains Runtime bundled within the IntelliJ Platform" to {
                intellijPlatform.singleOrNull()
                    ?.toPath()
                    .resolveRuntimeDirectory()
                    .ensureExecutableExists()
            },
            /**
             * Any other runtime resolved with the Java Toolchain.
             */
            "Java Runtime specified with Java Toolchain" to {
                javaToolchainSpec.languageVersion.orNull
                    ?.let { javaToolchainService.launcherFor(javaToolchainSpec).get() }
                    ?.metadata
                    ?.installationPath
                    ?.asPath
                    .resolveRuntimeDirectory()
                    .ensureExecutableExists()
            },
            /**
             * The current JVM used for running Gradle.
             */
            "Current JVM" to {
                Jvm.current().javaHome
                    .toPath()
                    .resolveRuntimeDirectory()
                    .ensureExecutableExists()
            },
        )

    /**
     * Resolves an exact Java Runtime executable.
     *
     * @see resolve
     * @return Java Runtime executable
     */
    fun resolveExecutable() = resolve()
        .resolveRuntimeExecutable()
        .throwIfNull { GradleException("No Java Runtime executable found") }

    /**
     * Resolves the Java Runtime directory for the given [Path].
     *
     * @return The resolved Java Runtime directory, or null if it does not exist.
     */
    private fun Path?.resolveRuntimeDirectory(): Path? {
        this ?: return null

        val jbr = listDirectoryEntries()
            .firstOrNull { it.name.startsWith("jbr") }
            ?.takeIfExists()

        return when {
            OperatingSystem.current().isMacOsX -> when {
                endsWith("Contents/Home") -> this
                jbr != null -> jbr.resolve("Contents/Home")
                else -> resolve("jdk/Contents/Home")
            }

            else -> when {
                jbr != null -> jbr
                else -> this
            }
        }.takeIfExists()
    }

    /**
     * Resolves the path to the Java Runtime executable within the parent directory.
     *
     * @return The resolved path to the runtime executable, or null if the executable does not exist.
     */
    private fun Path.resolveRuntimeExecutable(): Path? {
        val base = resolve("jre").takeIfExists().or(this)
        val extension = ".exe".takeIf { OperatingSystem.current().isWindows }.orEmpty()
        return base.resolve("bin/java$extension").takeIfExists()
    }

    /**
     * Ensures that the current Java Runtime directory contains executable.
     *
     * @return The original Java Runtime directory path if contains executable or null.
     */
    private fun Path?.ensureExecutableExists() = this
        ?.resolveRuntimeExecutable()
        ?.let { this }
}
