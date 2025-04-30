// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.platformPath
import java.nio.file.Path

private const val JETBRAINS_RUNTIME_VENDOR = "JetBrains"

/**
 * Resolves Java Runtime.
 *
 * @param jetbrainsRuntime The [Configurations.JETBRAINS_RUNTIME] configuration.
 * @param intellijPlatform The [Configurations.INTELLIJ_PLATFORM_DEPENDENCY] configuration.
 * @param javaToolchainSpec The Java Toolchain configured with [JavaPluginExtension].
 * @param javaToolchainService The [JavaToolchainService] used for finding a matching launcher.
 */
class JavaRuntimePathResolver(
    private val jetbrainsRuntime: FileCollection,
    private val intellijPlatform: FileCollection,
    private val javaToolchainSpec: JavaToolchainSpec,
    private val javaToolchainService: JavaToolchainService,
) : PathResolver() {

    override val subject = "Java Runtime"

    override val subjectInput
        get() = "jetbrainsRuntime[${jetbrainsRuntime.joinToString(":")}]," +
                "intellijPlatform[${intellijPlatform.joinToString(":")}]," +
                "javaToolchainSpec[vendor=${javaToolchainSpec.vendor},languageVersion=${javaToolchainSpec.languageVersion}]"

    override val predictions = sequenceOf(
        /**
         * The exact JetBrains Runtime archive provided to the [Configurations.JETBRAINS_RUNTIME] configuration using
         * the [IntelliJPlatformDependenciesExtension.jetbrainsRuntime] dependencies extensions.
         */
        "JetBrains Runtime specified with dependencies" to {
                jetbrainsRuntime.runCatching {
                singleOrNull()?.toPath()
                    .resolveRuntimeDirectory()
                    .ensureExecutableExists()
            }.getOrNull()
        },
        /**
         * The bundled JetBrains Runtime within the current IntelliJ Platform.
         */
        "JetBrains Runtime bundled within the IntelliJ Platform" to {
            intellijPlatform
                .platformPath()
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
     * @throws IllegalArgumentException
     */
    @Throws(IllegalArgumentException::class)
    fun resolveExecutable() = requireNotNull(resolve().resolveJavaRuntimeExecutable()) {
        "No Java Runtime executable found"
    }

    /**
     * Resolves the Java Runtime directory for the given [Path].
     *
     * @return The resolved Java Runtime directory, or null if it does not exist.
     */
    private fun Path?.resolveRuntimeDirectory(): Path? {
        this ?: return null

        val baseDirectory = resolveEntry("jbr*") ?: this
        return baseDirectory.resolveJavaRuntimeDirectory()
    }

    /**
     * Ensures that the current Java Runtime directory contains executable.
     *
     * @return The original Java Runtime directory path if contains executable or null.
     */
    private fun Path?.ensureExecutableExists() = runCatching {
        this?.resolveJavaRuntimeExecutable().let { this }
    }.getOrNull()
}

internal fun Path.resolveJavaRuntimeDirectory(): Path? =
    sequenceOf(
        { resolve("jdk/Contents/Home") },
        { resolve("Contents/Home") },
        { resolve("Home") },
        { this },
    ).firstNotNullOfOrNull {
        it().takeIfExists()
    }

/**
 * Resolves the path to the Java Runtime executable within the parent directory.
 *
 * @return The resolved path to the runtime executable, or null if the executable does not exist.
 * @throws GradleException
 */
@Throws(GradleException::class)
internal fun Path.resolveJavaRuntimeExecutable(): Path {
    val baseDirectory = resolve("jre").takeIfExists() ?: this

    return sequenceOf(
        { baseDirectory.resolve("bin/java") },
        { baseDirectory.resolve("bin/java.exe") },
    ).firstNotNullOfOrNull {
        it().takeIfExists()
    } ?: throw GradleException("No Java Runtime executable found")
}
