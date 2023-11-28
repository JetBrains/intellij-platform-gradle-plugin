// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.executableResolver

import com.jetbrains.plugin.structure.base.utils.exists
import com.jetbrains.plugin.structure.base.utils.listFiles
import com.jetbrains.plugin.structure.base.utils.simpleName
import org.gradle.api.file.FileCollection
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.JETBRAINS_RUNTIME_VENDOR
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.file.Path
import java.util.*

class JetBrainsRuntimeResolver(
    val jetbrainsRuntime: FileCollection,
    val intellijPlatform: FileCollection,
    val javaToolchainSpec: JavaToolchainSpec,
    val javaToolchainService: JavaToolchainService,
    val context: String? = null,
) : ExecutableResolver {

    override fun resolveExecutable() = directory?.getJbrRoot()?.let { root ->
        root
            .resolve("jre")
            .takeIf { it.exists() }
            .or(root)
            .resolve("bin/java" + ".exe".takeIf { OperatingSystem.current().isWindows }.orEmpty())
            .takeIf { it.exists() }
    }

    override fun resolveDirectory() = directory

    private val directory by lazy {
        debug(context, "Resolving runtime directory.")

        listOf(
            /**
             * Use JetBrains Runtime provided via [IntelliJPluginConstants.Configurations.JETBRAINS_RUNTIME_DEPENDENCY] configuration.
             * To add a custom JetBrains Runtime, use [org.jetbrains.intellij.platform.gradle.dependencies.jetbrainsRuntime]
             * or [org.jetbrains.intellij.platform.gradle.dependencies.jetbrainsRuntimeExplicit].
             */
            {
                jetbrainsRuntime.singleOrNull()?.let { file ->
                    file.toPath().getJbrRoot()
                        .also { debug(context, "JetBrains Runtime specified with dependencies resolved as: $it") }
                        .ifNull { debug(context, "Cannot resolve JetBrains Runtime: $file") }
                }
            },
            {
                javaToolchainSpec.vendor.orNull
                    ?.takeUnless { it == DefaultJvmVendorSpec.any() }
                    ?.takeIf {
                        @Suppress("UnstableApiUsage")
                        it.matches(JETBRAINS_RUNTIME_VENDOR)
                    }
                    ?.let { javaToolchainService.launcherFor(javaToolchainSpec).get() }
                    ?.let { javaLauncher ->
                        javaLauncher.metadata.installationPath.asPath.getJbrRoot()
                            .also { debug(context, "JetBrains Runtime specified with Java Toolchain resolved as: $it") }
                            .ifNull { debug(context, "Cannot resolve JetBrains Runtime specified with Java Toolchain") }
                    }
            },
            {
                intellijPlatform.singleOrNull()?.let { file ->
                    file.toPath().getJbrRoot()
                        .also { debug(context, "JetBrains Runtime bundled within IntelliJ Platform resolved as: $it") }
                        .ifNull { debug(context, "Cannot resolve JetBrains Runtime bundled within IntelliJ Platform: $file") }
                }
            },
            {
                Jvm.current().javaHome.toPath().getJbrRoot()
                    .also { debug(context, "Using current JVM: $it") }
                    .ifNull { debug(context, "Cannot resolve current JVM") }
            },
        )
            .asSequence()
            .mapNotNull { it() }
            .firstOrNull()
            ?.also { info(context, "Resolved JetBrains Runtime directory: $it") }
    }

    private fun getBuiltinJbrVersion(ideDirectory: File): String? {
        val dependenciesFile = File(ideDirectory, "dependencies.txt")
        if (dependenciesFile.exists()) {
            val properties = Properties()
            val reader = FileReader(dependenciesFile)
            try {
                properties.load(reader)
                return properties.getProperty("runtimeBuild") ?: properties.getProperty("jdkBuild")
            } catch (ignore: IOException) {
            } finally {
                reader.close()
            }
        }
        return null
    }

    private fun Path.getJbrRoot(): Path {
        val jbr = listFiles().firstOrNull { it.simpleName.startsWith("jbr") }?.takeIf { it.exists() }

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
        }
    }
}
