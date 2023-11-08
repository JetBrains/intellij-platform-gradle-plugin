// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.executableResolver.IntelliJPluginVerifierResolver
import org.jetbrains.intellij.platform.gradle.executableResolver.JetBrainsRuntimeResolver
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.info
import org.jetbrains.intellij.platform.gradle.isSpecified
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.tasks.base.*
import org.jetbrains.intellij.platform.gradle.throwIfNull
import java.util.*
import kotlin.io.path.createDirectories

internal inline fun <reified T : Task> Project.configureTask(vararg names: String, noinline configuration: T.() -> Unit = {}) {
    names.forEach { name ->
        info(null, "Configuring task: $name")
        tasks.maybeCreate<T>(name)
    }

    tasks.withType<T> {
        if (this is PlatformVersionAware) {
            intelliJPlatform = configurations.getByName(Configurations.INTELLIJ_PLATFORM)
            intelliJPlatformProductInfo = configurations.getByName(Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO)
        }

        if (this is CoroutinesJavaAgentAware) {
            val initializeIntelliJPlatformPluginTaskProvider = tasks.named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)

            coroutinesJavaAgentFile.convention(initializeIntelliJPlatformPluginTaskProvider.flatMap {
                it.coroutinesJavaAgent
            })

            dependsOn(initializeIntelliJPlatformPluginTaskProvider)
        }

        if (this is CustomPlatformVersionAware) {
            val suffix = UUID.randomUUID().toString().substring(0, 8)
            val intellijPlatformDependencyConfiguration =
                configurations.create("${Configurations.INTELLIJ_PLATFORM_DEPENDENCY}_$suffix") {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true

                    this@configureTask.dependencies
                        .the<IntelliJPlatformDependenciesExtension>()
                        .create(type, version, configurationName = name)
                }
            val intellijPlatformConfiguration = configurations.create("${Configurations.INTELLIJ_PLATFORM}_$suffix") {
                isVisible = false
                isCanBeConsumed = false
                isCanBeResolved = true

                attributes {
                    attribute(Attributes.extracted, true)
                }

                extendsFrom(intellijPlatformDependencyConfiguration)
            }
            val intellijPlatformProductInfoConfiguration =
                configurations.create("${Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO}_$suffix") {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true

                    attributes {
                        attribute(Attributes.productInfo, true)
                    }

                    extendsFrom(intellijPlatformConfiguration)
                }

            type.convention(provider {
                val productInfo = configurations.getByName(Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO)
                    .singleOrNull()
                    .throwIfNull { GradleException("IntelliJ Platform is not specified.") }
                    .toPath()
                    .productInfo()
                IntelliJPlatformType.fromCode(productInfo.productCode)
            })
            version.convention(provider {
                val productInfo = configurations.getByName(Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO)
                    .singleOrNull()
                    .throwIfNull { GradleException("IntelliJ Platform is not specified.") }
                    .toPath()
                    .productInfo()
                IdeVersion.createIdeVersion(productInfo.version).toString()
            })
            intelliJPlatform.setFrom(provider {
                when {
                    type.isSpecified() || version.isSpecified() -> intellijPlatformConfiguration
                    else -> emptyList()
                }
            })
            intelliJPlatformProductInfo.setFrom(provider {
                when {
                    type.isSpecified() || version.isSpecified() -> intellijPlatformProductInfoConfiguration
                    else -> emptyList()
                }
            })
        }

        if (this is SandboxAware) {
            val prepareSandboxTaskProvider = tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
            val extension = the<IntelliJPlatformExtension>()

            sandboxDirectory.convention(extension.sandboxContainer.map {
                it.dir("$platformType-$platformVersion").also { directory ->
                    directory.asFile.toPath().createDirectories()
                }
            })

            prepareSandboxTaskProvider.configure {
                sandboxDirectory.convention(this@withType.sandboxDirectory)
            }

            dependsOn(prepareSandboxTaskProvider)
        }

        if (this is JetBrainsRuntimeAware) {
            val jbrResolver = JetBrainsRuntimeResolver(
                jetbrainsRuntime = configurations.getByName(Configurations.JETBRAINS_RUNTIME),
                intellijPlatform = intelliJPlatform,
                javaToolchainSpec = project.the<JavaPluginExtension>().toolchain,
                javaToolchainService = serviceOf<JavaToolchainService>(),
            )

            jetbrainsRuntimeDirectory.convention(layout.dir(provider {
                jbrResolver.resolveDirectory()?.toFile()
            }))
            jetbrainsRuntimeExecutable.convention(layout.file(provider {
                jbrResolver.resolveExecutable()?.toFile()
            }))
        }

        if (this is TestIdeTask) {
            executable(jetbrainsRuntimeExecutable)
        }

        if (this is PluginVerifierAware) {
            val extension = the<IntelliJPlatformExtension>()
            val pluginVerifierResolver = IntelliJPluginVerifierResolver(
                intellijPluginVerifier = configurations.getByName(Configurations.INTELLIJ_PLUGIN_VERIFIER),
                localPath = extension.pluginVerifier.path,
            )

            pluginVerifierDirectory.convention(layout.dir(provider {
                pluginVerifierResolver.resolveDirectory()?.toFile()
            }))
            pluginVerifierExecutable.convention(layout.file(provider {
                pluginVerifierResolver.resolveExecutable()?.toFile()
            }))
        }
    }

    tasks.withType<T>(configuration)
}
