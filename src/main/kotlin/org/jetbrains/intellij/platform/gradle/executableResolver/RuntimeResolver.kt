// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.executableResolver

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.JETBRAINS_RUNTIME_VENDOR
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.utils.*
import java.nio.file.Path
import kotlin.io.path.exists
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
class RuntimeResolver(
    val jetbrainsRuntime: FileCollection,
    val intellijPlatform: FileCollection,
    val javaToolchainSpec: JavaToolchainSpec,
    val javaToolchainService: JavaToolchainService,
) : ExecutableResolver {

    private val log = Logger(javaClass)

    /**
     * Resolves Java Runtime with:
     * - The exact JetBrains Runtime archive provided to the [Configurations.JETBRAINS_RUNTIME] configuration using
     *   [IntelliJPlatformDependenciesExtension.jetbrainsRuntime] dependencies extensions.
     * - Java Toolchain if the toolchain vendor matches [JETBRAINS_RUNTIME_VENDOR].
     * - The bundled JetBrains Runtime within the current IntelliJ Platform.
     * - Any other runtime resolved with the Java Toolchain.
     * - The JVM currently used to run Gradle.
     *
     * @return Java Runtime executable path
     * @throws GradleException if no Java Runtime found
     */
    override fun resolve() = listOf(
        {
            jetbrainsRuntime.singleOrNull()?.let { file ->
                file.toPath().resolveRuntimeDirectory()
                    .also { log.debug("JetBrains Runtime specified with dependencies resolved as: $it") }
                    .ensureExecutableExists()
                    .ifNull { log.debug("Cannot resolve JetBrains Runtime: $file") }
            }
        },
        {
            @Suppress("UnstableApiUsage")
            javaToolchainSpec.vendor.orNull
                ?.takeUnless { it == DefaultJvmVendorSpec.any() }
                ?.takeIf { it.matches(JETBRAINS_RUNTIME_VENDOR) }
                ?.let { javaToolchainService.launcherFor(javaToolchainSpec).get() }
                ?.let { javaLauncher ->
                    javaLauncher.metadata.installationPath.asPath.resolveRuntimeDirectory()
                        .also { log.debug("JetBrains Runtime specified with Java Toolchain resolved as: $it") }
                        .ensureExecutableExists()
                        .ifNull { log.debug("Cannot resolve JetBrains Runtime specified with Java Toolchain") }
                }
        },
        {
            intellijPlatform.singleOrNull()?.let { file ->
                file.toPath().resolveRuntimeDirectory()
                    .also { log.debug("JetBrains Runtime bundled within IntelliJ Platform resolved as: $it") }
                    .ensureExecutableExists()
                    .ifNull { log.debug("Cannot resolve JetBrains Runtime bundled within IntelliJ Platform: $file") }
            }
        },
        {
            javaToolchainSpec.languageVersion.orNull
                ?.let { javaToolchainService.launcherFor(javaToolchainSpec).get() }
                ?.let { javaLauncher ->
                    javaLauncher.metadata.installationPath.asPath.resolveRuntimeDirectory()
                        .also { log.debug("Java Runtime specified with Java Toolchain resolved as: $it") }
                        .ensureExecutableExists()
                        .ifNull { log.debug("Cannot resolve Java Runtime specified with Java Toolchain") }
                }
        },
        {
            Jvm.current().javaHome.toPath().resolveRuntimeDirectory()
                .also { log.debug("Using current JVM: $it") }
                .ensureExecutableExists()
                .ifNull { log.debug("Cannot resolve current JVM") }
        },
    )
        .also { log.debug("Resolving Java Runtime directory.") }
        .asSequence()
        .mapNotNull { it() }
        .firstOrNull()
        ?.also { log.info("Resolved Java Runtime directory: $it") }
        .throwIfNull { GradleException("No Java Runtime found") }

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
    private fun Path.resolveRuntimeDirectory(): Path? {
        val jbr = listDirectoryEntries()
            .firstOrNull { it.name.startsWith("jbr") }
            ?.takeIf { it.exists() }

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
        }.takeIf { it.exists() }
    }

    /**
     * Resolves the path to the Java Runtime executable within the parent directory.
     *
     * @return The resolved path to the runtime executable, or null if the executable does not exist.
     */
    private fun Path.resolveRuntimeExecutable(): Path? {
        val base = resolve("jre").takeIf { it.exists() }.or(this)
        val extension = ".exe".takeIf { OperatingSystem.current().isWindows }.orEmpty()
        return base.resolve("bin/java$extension").takeIf { it.exists() }
    }

    /**
     * Ensures that the current Java Runtime directory contains executable.
     *
     * @return The original Java Runtime directory path if contains executable or null.
     */
    private fun Path?.ensureExecutableExists() = this
        ?.resolveRuntimeExecutable()
        .ifNull { log.debug("Java Runtime executable not found in: $this") }
        ?.let { this }
}
