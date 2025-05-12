// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.*
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Extensions
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatform
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.artifacts.LocalIvyArtifactPathComponentMetadataRule
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.RuntimeAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.SplitModeTarget
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware
import org.jetbrains.intellij.platform.gradle.utils.create
import org.jetbrains.intellij.platform.gradle.utils.isModule
import org.jetbrains.intellij.platform.gradle.utils.rootProjectPath
import org.jetbrains.intellij.platform.gradle.utils.settings
import javax.inject.Inject

@IntelliJPlatform
abstract class IntelliJPlatformTestingExtension @Inject constructor(
    private val project: Project,
    private val dependenciesHelper: IntelliJPlatformDependenciesHelper,
) : ExtensionAware {

    val runIde = register<RunIdeParameters, _>()
    val testIde = register<TestIdeParameters, _>()
    val testIdeUi = register<TestIdeUiParameters, _>()
    val testIdePerformance = register<TestIdePerformanceParameters, _>()

    private inline fun <reified C : CommonParameters<T>, reified T> register() where T : Task, T : IntelliJPlatformVersionAware, T : RuntimeAware, T : SandboxAware =
        project.objects.domainObjectContainer(
            elementType = C::class,
            factory = { project.objects.newInstance<C>(it, project) },
        ).apply {
            all {
                if (project.pluginManager.isModule) {
                    when (T::class) {
                        RunIdeTask::class,
                        TestIdeUiTask::class,
                        TestIdePerformanceTask::class,
                            -> throw GradleException("The ${Extensions.INTELLIJ_PLATFORM_TESTING} { ... } extension within a module can register `testIde` tasks only.")
                    }
                }

                val dependenciesExtension = project.dependencies.the<IntelliJPlatformDependenciesExtension>()
                val baseIntelliJPlatformLocalConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_LOCAL]
                val basePrepareSandboxTask = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
                val plugins = IntelliJPlatformPluginsExtension.register(dependenciesHelper, project.objects, target = this)

                val customIntelliJPlatformLocalConfiguration = project.configurations.create(
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

                val customIntelliJPlatformDependencyConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_DEPENDENCY_ARCHIVE.withSuffix,
                    description = "Custom IntelliJ Platform dependency archive",
                ) {
                    dependenciesExtension.customCreate(
                        type = type,
                        version = version,
                        useInstaller = useInstaller,
                        configurationName = this@create.name,
                        intellijPlatformConfigurationName = Configurations.INTELLIJ_PLATFORM_DEPENDENCY.withSuffix,
                    )
                }

                val customIntelliJPlatformConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_DEPENDENCY.withSuffix,
                    description = "Custom IntelliJ Platform",
                ) {
                    attributes {
                        attribute(Attributes.extracted, true)
                    }

                    defaultDependencies {
                        addAllLater(project.provider {
                            listOf(
                                customIntelliJPlatformLocalConfiguration,
                                customIntelliJPlatformDependencyConfiguration,
                                baseIntelliJPlatformLocalConfiguration,
                            ).flatMap { it.dependencies }.take(1)
                        })
                    }
                    defaultDependencies {
                        addAllLater(project.provider {
                            customIntelliJPlatformDependencyConfiguration.dependencies
                        })
                    }

                    LocalIvyArtifactPathComponentMetadataRule.register(
                        configuration = this,
                        dependencies = project.dependencies,
                        providers = project.providers,
                        settings = project.settings,
                        rootProjectDirectory = project.rootProjectPath,
                    )
                }
                val customJetBrainsRuntimeConfiguration = project.configurations.create(
                    name = Configurations.JETBRAINS_RUNTIME.withSuffix,
                    description = "Custom JetBrains Runtime",
                ) {
                    attributes {
                        attribute(Attributes.extracted, true)
                    }

                    defaultDependencies {
                        addLater(
                            dependenciesHelper.obtainJetBrainsRuntimeVersion(customIntelliJPlatformConfiguration.name)
                                .map { version -> dependenciesHelper.createJetBrainsRuntime(version) }
                        )
                    }
                }

                val customIntellijPlatformTestPluginDependencyConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_DEPENDENCY.withSuffix,
                    description = "Custom IntelliJ Platform test plugin dependencies",
                ) {
                    extendsFrom(project.configurations[Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_DEPENDENCY])
                }

                val customIntellijPlatformTestPluginLocalConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_LOCAL.withSuffix,
                    description = "Custom IntelliJ Platform test plugin local",
                ) {
                    attributes {
                        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(Attributes.DISTRIBUTION_NAME))
                    }

                    extendsFrom(project.configurations[Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_LOCAL])
                }

                val customIntellijPlatformTestPluginConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN.withSuffix,
                    description = "Custom IntelliJ Platform plugins",
                ) {
                    attributes {
                        attribute(Attributes.extracted, true)
                        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(Attributes.DISTRIBUTION_NAME))
                    }

                    extendsFrom(customIntellijPlatformTestPluginDependencyConfiguration)
                    extendsFrom(customIntellijPlatformTestPluginLocalConfiguration)
                }

                val customIntellijPlatformTestBundledPluginsConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_PLUGINS.withSuffix,
                    description = "Custom IntelliJ Platform test bundled plugins",
                ) {
                    extendsFrom(project.configurations[Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_PLUGINS])
                }
                val customIntellijPlatformTestBundledModulesConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_MODULES.withSuffix,
                    description = "Custom IntelliJ Platform test bundled modules",
                ) {
                    extendsFrom(project.configurations[Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_MODULES])
                }

                val customIntellijPlatformTestDependenciesConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES.withSuffix,
                    description = "Custom IntelliJ Platform Test Dependencies"
                ) {
                    extendsFrom(
                        customIntellijPlatformTestPluginConfiguration,
                        customIntellijPlatformTestBundledPluginsConfiguration,
                        customIntellijPlatformTestBundledModulesConfiguration,
                    )
                }

                val customIntellijPlatformTestClasspathConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_TEST_CLASSPATH.withSuffix,
                    description = "Custom IntelliJ Platform Test Classpath",
                ) {
                    attributes {
                        attribute(Attributes.extracted, true)
                        attribute(Attributes.collected, true)
                    }

                    extendsFrom(customIntelliJPlatformConfiguration)
                    extendsFrom(customIntellijPlatformTestDependenciesConfiguration)
                }
                val customIntellijPlatformTestRuntimeClasspathConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_TEST_RUNTIME_CLASSPATH.withSuffix,
                    description = "Custom IntelliJ Platform Test Runtime Classpath",
                ) {
                    attributes
                        .attribute(Attributes.extracted, true)
                        .attribute(Attributes.collected, true)

                    extendsFrom(project.configurations[Configurations.INTELLIJ_PLATFORM_TEST_RUNTIME_CLASSPATH])
                    extendsFrom(customIntellijPlatformTestDependenciesConfiguration)
                }

                val customIntelliJPlatformTestRuntimeFixClasspathConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_TEST_RUNTIME_FIX_CLASSPATH.withSuffix,
                    description = "Custom IntelliJ Platform Test Runtime Fix Classpath",
                ) {
                    defaultDependencies {
                        addAllLater(project.providers[GradleProperties.AddDefaultIntelliJPlatformDependencies].map { enabled ->
                            val platformPath = runCatching { dependenciesHelper.platformPath(customIntelliJPlatformConfiguration.name) }.getOrNull()
                            when (enabled && platformPath != null) {
                                true -> dependenciesHelper.createIntelliJPlatformTestRuntime(platformPath)
                                false -> null
                            }.let { listOfNotNull(it) }
                        })
                    }
                }

                plugins {
                    intellijPlatformConfigurationName = customIntelliJPlatformConfiguration.name
                    intellijPlatformPluginDependencyConfigurationName = customIntellijPlatformTestPluginDependencyConfiguration.name
                    intellijPlatformPluginLocalConfigurationName = customIntellijPlatformTestPluginLocalConfiguration.name
                    intellijPlatformTestBundledPluginsConfiguration = customIntellijPlatformTestBundledPluginsConfiguration.name
                    intellijPlatformTestBundledModulesConfiguration = customIntellijPlatformTestBundledModulesConfiguration.name
                }

                val prepareSandboxTask = project.tasks.register<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX.withSuffix) {
                    group = Plugin.GROUP_NAME

                    intelliJPlatformConfiguration = customIntelliJPlatformConfiguration
                    intelliJPlatformPluginConfiguration = customIntellijPlatformTestPluginConfiguration

                    sandboxDirectory = this@all.sandboxDirectory.orElse(basePrepareSandboxTask.flatMap { it.sandboxDirectory })
                    splitMode = this@all.splitMode.orElse(basePrepareSandboxTask.flatMap { it.splitMode })
                    splitModeTarget = this@all.splitModeTarget.orElse(basePrepareSandboxTask.flatMap { it.splitModeTarget })
                    disabledPlugins = plugins.disabled
                }

                project.tasks.register<T>(name) {
                    group = Plugin.GROUP_NAME

                    intelliJPlatformConfiguration = customIntelliJPlatformConfiguration
                    intelliJPlatformPluginConfiguration = customIntellijPlatformTestPluginConfiguration
                    jetbrainsRuntimeConfiguration = customJetBrainsRuntimeConfiguration

                    if (this is TestableAware) {
                        intellijPlatformTestClasspathConfiguration = customIntellijPlatformTestClasspathConfiguration
                        intellijPlatformTestRuntimeClasspathConfiguration = customIntellijPlatformTestRuntimeClasspathConfiguration
                        intelliJPlatformTestRuntimeFixClasspathConfiguration = customIntelliJPlatformTestRuntimeFixClasspathConfiguration
                    }

                    applySandboxFrom(prepareSandboxTask)
                }
            }
        }

    companion object {
        fun register(project: Project, dependenciesHelper: IntelliJPlatformDependenciesHelper, target: Any) =
            target.configureExtension<IntelliJPlatformTestingExtension>(
                Extensions.INTELLIJ_PLATFORM_TESTING,
                project,
                dependenciesHelper,
            )
    }

    abstract class CommonParameters<T : Task> @Inject constructor(
        private val name: String,
        private val project: Project,
        private val taskClass: Class<T>,
    ) : Named, ExtensionAware, Buildable {

        abstract val type: Property<IntelliJPlatformType>
        abstract val version: Property<String>
        abstract val localPath: DirectoryProperty
        abstract val useInstaller: Property<Boolean>

        abstract val sandboxDirectory: DirectoryProperty

        abstract val splitMode: Property<Boolean>
        abstract val splitModeTarget: Property<SplitModeTarget>

        fun plugins(configuration: Action<IntelliJPlatformPluginsExtension>) {
            configuration.execute(the<IntelliJPlatformPluginsExtension>())
        }

        val task
            get() = project.tasks.named(name, taskClass)

        fun task(action: Action<in T>) {
            task.configure(action)
        }

        val prepareSandboxTask
            get() = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX.withSuffix)

        fun prepareSandboxTask(action: Action<in PrepareSandboxTask>) {
            prepareSandboxTask.configure(action)
        }

        override fun getName() = name

        override fun getBuildDependencies() = DefaultTaskDependency().add(task)

        internal val String.withSuffix get() = "${this}_${name}"
    }

    abstract class RunIdeParameters @Inject constructor(name: String, project: Project) :
        CommonParameters<RunIdeTask>(name, project, RunIdeTask::class.java)

    abstract class TestIdeParameters @Inject constructor(name: String, project: Project) :
        CommonParameters<TestIdeTask>(name, project, TestIdeTask::class.java)

    abstract class TestIdeUiParameters @Inject constructor(name: String, project: Project) :
        CommonParameters<TestIdeUiTask>(name, project, TestIdeUiTask::class.java)

    abstract class TestIdePerformanceParameters @Inject constructor(name: String, project: Project) :
        CommonParameters<TestIdePerformanceTask>(name, project, TestIdePerformanceTask::class.java)
}
