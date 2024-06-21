// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Extensions
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPlatform
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.SplitModeTarget
import org.jetbrains.intellij.platform.gradle.utils.create
import javax.inject.Inject

@IntelliJPlatform
abstract class IntelliJPlatformTestingExtension @Inject constructor(private val project: Project) : ExtensionAware {

    val runIde = register<RunIdeParameters, _>()
    val testIde = register<TestIdeParameters, _>()
    val testIdeUi = register<TestIdeUiParameters, _>()
    val testIdePerformance = register<TestIdePerformanceParameters, _>()

    private inline fun <reified C : CommonParameters<T>, T> register() where T : Task, T : IntelliJPlatformVersionAware, T : SandboxAware = project.objects.domainObjectContainer(
        elementType = C::class,
        factory = { project.objects.newInstance<C>(it, project) },
    ).apply {
        all {
            plugins {
                intellijPlatformPluginDependencyConfigurationName = this@all.intellijPlatformPluginDependencyConfiguration.name
                intellijPlatformPluginLocalConfigurationName = this@all.intellijPlatformPluginLocalConfiguration.name
            }

            prepareSandboxTask {
                sandboxDirectory = this@all.sandboxDirectory.orElse(basePrepareSandboxTask.flatMap { it.sandboxDirectory })
                splitMode = this@all.splitMode.orElse(basePrepareSandboxTask.flatMap { it.splitMode })
                splitModeTarget = this@all.splitModeTarget.orElse(basePrepareSandboxTask.flatMap { it.splitModeTarget })
                disabledPlugins = this@all.plugins.disabled
            }

            task {
                sandboxProducer = prepareSandboxTask.name
                intelliJPlatformConfiguration = this@all.intelliJPlatformConfiguration
                intelliJPlatformPluginConfiguration = this@all.intellijPlatformPluginConfiguration
            }
        }
    }

    companion object : Registrable<IntelliJPlatformTestingExtension> {
        override fun register(project: Project, target: Any) = target.configureExtension<IntelliJPlatformTestingExtension>(Extensions.INTELLIJ_PLATFORM_TESTING, project)
    }

    abstract class CommonParameters<T : Task> @Inject constructor(private val name: String, project: Project, taskClass: Class<T>) : Named, ExtensionAware {

        abstract val type: Property<IntelliJPlatformType>
        abstract val version: Property<String>
        abstract val localPath: DirectoryProperty

        abstract val sandboxDirectory: DirectoryProperty

        abstract val splitMode: Property<Boolean>
        abstract val splitModeTarget: Property<SplitModeTarget>

        fun plugins(configuration: Action<IntelliJPlatformPluginsExtension>) {
            configuration.execute(the<IntelliJPlatformPluginsExtension>())
        }

        val task = project.tasks.register(name, taskClass)
        val prepareSandboxTask = project.tasks.register<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX.withSuffix)

        internal val basePrepareSandboxTask = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
        internal val plugins = IntelliJPlatformPluginsExtension.register(project, target = this)

        private val dependenciesExtension = project.dependencies.the<IntelliJPlatformDependenciesExtension>()
        private val baseIntelliJPlatformLocalConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_LOCAL]

        private val intelliJPlatformLocalConfiguration = project.configurations.create(
            name = Configurations.INTELLIJ_PLATFORM_LOCAL.withSuffix,
            description = "Custom IntelliJ Platform local",
        ) {
            attributes {
                attribute(Attributes.extracted, true)
            }
            dependenciesExtension.local(
                localPath = localPath,
                configurationName = this@create.name,
            )
        }

        private val intelliJPlatformDependencyConfiguration = project.configurations.create(
            name = Configurations.INTELLIJ_PLATFORM_DEPENDENCY.withSuffix,
            description = "Custom IntelliJ Platform dependency archive",
        ) {
            dependenciesExtension.customCreate(
                type = type,
                version = version,
                configurationName = this@create.name,
            )
        }

        internal val intelliJPlatformConfiguration = project.configurations.create(
            name = Configurations.INTELLIJ_PLATFORM.withSuffix,
            description = "Custom IntelliJ Platform",
        ) {
            attributes {
                attribute(Attributes.extracted, true)
            }

            defaultDependencies {
                addAllLater(project.provider {
                    listOf(
                        intelliJPlatformLocalConfiguration,
                        intelliJPlatformDependencyConfiguration,
                        baseIntelliJPlatformLocalConfiguration,
                    ).flatMap { it.dependencies }.take(1)
                })
            }
            defaultDependencies {
                addAllLater(project.provider {
                    intelliJPlatformDependencyConfiguration.dependencies
                })
            }
        }

        internal val intellijPlatformPluginDependencyConfiguration = project.configurations.create(
            name = Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY.withSuffix,
            description = "Custom IntelliJ Platform plugin dependencies",
        ) {
            project.configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY_COLLECTOR].extendsFrom(this)
            extendsFrom(project.configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY])
        }

        internal val intellijPlatformPluginLocalConfiguration = project.configurations.create(
            name = Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL.withSuffix,
            description = "Custom IntelliJ Platform plugin local",
        ) {
            extendsFrom(project.configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL])
        }

        internal val intellijPlatformPluginConfiguration = project.configurations.create(
            name = Configurations.INTELLIJ_PLATFORM_PLUGIN.withSuffix,
            description = "Custom IntelliJ Platform plugins",
        ) {
            attributes {
                attribute(Attributes.extracted, true)
            }

            extendsFrom(intellijPlatformPluginDependencyConfiguration)
            extendsFrom(intellijPlatformPluginLocalConfiguration)
        }

        override fun getName() = name

        private val String.withSuffix get() = "${this}_${name}"
    }

    abstract class RunIdeParameters @Inject constructor(name: String, project: Project) : CommonParameters<RunIdeTask>(name, project, RunIdeTask::class.java)
    abstract class TestIdeParameters @Inject constructor(name: String, project: Project) : CommonParameters<TestIdeTask>(name, project, TestIdeTask::class.java)
    abstract class TestIdeUiParameters @Inject constructor(name: String, project: Project) : CommonParameters<TestIdeUiTask>(name, project, TestIdeUiTask::class.java)
    abstract class TestIdePerformanceParameters @Inject constructor(name: String, project: Project) : CommonParameters<TestIdePerformanceTask>(name, project, TestIdePerformanceTask::class.java)
}
