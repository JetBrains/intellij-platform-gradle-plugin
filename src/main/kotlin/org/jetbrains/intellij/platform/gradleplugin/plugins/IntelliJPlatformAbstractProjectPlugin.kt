// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Extensions
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradleplugin.checkGradleVersion
import org.jetbrains.intellij.platform.gradleplugin.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradleplugin.info
import org.jetbrains.intellij.platform.gradleplugin.isSpecified
import org.jetbrains.intellij.platform.gradleplugin.jbr.JetBrainsRuntimeResolver
import org.jetbrains.intellij.platform.gradleplugin.logCategory
import org.jetbrains.intellij.platform.gradleplugin.tasks.InitializeIntelliJPlatformPluginTask
import org.jetbrains.intellij.platform.gradleplugin.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradleplugin.tasks.base.*
import kotlin.io.path.createDirectories

abstract class IntelliJPlatformAbstractProjectPlugin(val pluginId: String) : Plugin<Project> {

    protected lateinit var context: String

    final override fun apply(project: Project) {
        context = project.logCategory()

        info(context, "Configuring plugin: $pluginId")
        checkGradleVersion()

        project.configure()
    }

    protected abstract fun Project.configure()

    protected inline fun <reified T : Task> TaskContainer.configureTask(vararg names: String, noinline configuration: T.() -> Unit = {}) {
        names.forEach { name ->
            info(context, "Configuring task: $name")
            maybeCreate<T>(name)
        }

        withType<T> {
            if (this is CoroutinesJavaAgentAware) {
                val initializeIntelliJPlatformPluginTaskProvider = named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)

                coroutinesJavaAgentFile.convention(initializeIntelliJPlatformPluginTaskProvider.flatMap {
                    it.coroutinesJavaAgent
                })

                dependsOn(initializeIntelliJPlatformPluginTaskProvider)
            }

            if (this is CustomPlatformAware) {
                intelliJPlatform = project.configurations.getByName(Configurations.INTELLIJ_PLATFORM)
                intellijPlatformDirectory.convention(
                    project.layout.dir(
                        project.provider {
                            when {
                                !type.isSpecified() && !version.isSpecified() -> intelliJPlatform.singleFile
                                else -> throw GradleException("Foo")
                            }
                        }
                    )
                )
            }

            if (this is PlatformVersionAware) {
                intelliJPlatformProductInfo = project.configurations.getByName(Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO)
            }

            if (this is SandboxAware) {
                val prepareSandboxTaskProvider = named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)

                sandboxDirectory.convention(project.intelliJPlatformExtension.sandboxContainer.map {
                    it.dir("$platformType-$platformVersion").also { directory ->
                        directory.asFile.toPath().createDirectories()
                    }
                })

                prepareSandboxTaskProvider.configure {
                    sandboxDirectory.convention(this@withType.sandboxDirectory)
                }

                dependsOn(prepareSandboxTaskProvider)
            }

            val jbrResolver = JetBrainsRuntimeResolver(
                jetbrainsRuntimeConfiguration = project.configurations.getByName(Configurations.JETBRAINS_RUNTIME),
                intellijPlatformConfiguration = project.configurations.getByName(Configurations.INTELLIJ_PLATFORM),
                javaToolchainSpec = project.extensions.getByType<JavaPluginExtension>().toolchain,
                javaToolchainService = project.serviceOf<JavaToolchainService>(),
            )

            if (this is JetBrainsRuntimeAware) {
                jetbrainsRuntimeDirectory.convention(project.layout.dir(project.provider {
                    jbrResolver.resolveDirectory()?.toFile()
                }))
                jetbrainsRuntimeExecutable.convention(project.layout.file(project.provider {
                    jbrResolver.resolveExecutable()?.toFile()
                }))
            }

            if (this is Test) {
                executable(project.provider {
                    jbrResolver.resolveExecutable()?.toFile()
                })
            }
        }

        withType<T>(configuration)
    }

    protected inline fun <reified T : Any> Any.configureExtension(name: String, noinline configuration: T.() -> Unit = {}) {
        info(context, "Configuring extension: $name")
        with((this as ExtensionAware).extensions) {
            val extension = findByName(name) as? T ?: create<T>(name)
            extension.configuration()
        }
    }

    protected val Project.intelliJPlatformExtension
        get() = extensions.getByName<IntelliJPlatformExtension>(Extensions.INTELLIJ_PLATFORM)
}
