// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.*
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.Constants.Extensions
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.artifacts.LocalIvyArtifactPathComponentMetadataRule
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.RuntimeAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.SplitModeTarget
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware
import org.jetbrains.intellij.platform.gradle.utils.*
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
            factory = { project.objects.newInstance<C>(it, project, dependenciesHelper) },
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

                val customIntellijPlatformConfigurationName = Configurations.INTELLIJ_PLATFORM_DEPENDENCY.withSuffix

                val customIntelliJPlatformLocalConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_LOCAL.withSuffix,
                    description = "Custom IntelliJ Platform local",
                ) {
                    attributes {
                        attribute(Attributes.extracted, true)
                    }
                    dependenciesExtension.customLocal(
                        localPath = localPath,
                        configurationName = this@create.name,
                        intellijPlatformConfigurationName = customIntellijPlatformConfigurationName,
                    )
                }

                val customIntelliJPlatformDependencyConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_DEPENDENCY_ARCHIVE.withSuffix,
                    description = "Custom IntelliJ Platform dependency archive",
                )

                dependenciesExtension.create(
                    configurations = listOf(this@all),
                    dependencyConfigurationName = customIntellijPlatformConfigurationName,
                    dependencyArchivesConfigurationName = customIntelliJPlatformDependencyConfiguration.name,
                    localArchivesConfigurationName = customIntelliJPlatformLocalConfiguration.name,
                )

                val customIntelliJPlatformConfiguration = project.configurations.create(
                    name = customIntellijPlatformConfigurationName,
                    description = "Custom IntelliJ Platform",
                ) {
                    attributes {
                        attribute(Attributes.extracted, true)
                    }

                    defaultDependencies {
                        addAllLater(project.provider {
                            listOf(
                                baseIntelliJPlatformLocalConfiguration,
                                customIntelliJPlatformDependencyConfiguration,
                                customIntelliJPlatformLocalConfiguration,
                            ).flatMap { it.dependencies }.takeLast(1)
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
                        addAllLater(
                            dependenciesHelper.createJetBrainsRuntimeObtainedDependency(
                                customIntelliJPlatformConfiguration.name,
                            ),
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
                }
                val customIntellijPlatformTestBundledModulesConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_MODULES.withSuffix,
                    description = "Custom IntelliJ Platform test bundled modules",
                ) {
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

                val customIntellijPlatformClasspathConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_CLASSPATH.withSuffix,
                    description = "Custom IntelliJ Platform Classpath",
                ) {
                    attributes {
                        attribute(Attributes.extracted, true)
                        attribute(Attributes.collected, true)
                    }

                    extendsFrom(customIntelliJPlatformConfiguration)
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

                    attributes {
                        attributes.attribute(Attributes.kotlinJPlatformType, "jvm")
                    }

                    extendsFrom(project.configurations[Configurations.INTELLIJ_PLATFORM_TEST_RUNTIME_CLASSPATH])
                    extendsFrom(customIntellijPlatformTestDependenciesConfiguration)
                }

                val customIntelliJPlatformTestRuntimeFixClasspathConfiguration = project.configurations.create(
                    name = Configurations.INTELLIJ_PLATFORM_TEST_RUNTIME_FIX_CLASSPATH.withSuffix,
                    description = "Custom IntelliJ Platform Test Runtime Fix Classpath",
                ) {
                    defaultDependencies {
                        addAllLater(
                            project.providers[GradleProperties.AddDefaultIntelliJPlatformDependencies].map { enabled ->
                                buildList {
                                    if (enabled) {
                                        runCatching {
                                            dependenciesHelper.platformPathProvider(customIntelliJPlatformConfiguration.name)
                                                .get()
                                        }.onSuccess { add(dependenciesHelper.createIntelliJPlatformTestRuntime(it)) }
                                    }
                                }
                            },
                        )
                    }
                }

                plugins {
                    intellijPlatformConfigurationName = customIntelliJPlatformConfiguration.name
                    intellijPlatformPluginDependencyConfigurationName = customIntellijPlatformTestPluginDependencyConfiguration.name
                    intellijPlatformPluginLocalConfigurationName = customIntellijPlatformTestPluginLocalConfiguration.name
                    intellijPlatformTestBundledPluginsConfiguration = customIntellijPlatformTestBundledPluginsConfiguration.name
                    intellijPlatformTestBundledModulesConfiguration = customIntellijPlatformTestBundledModulesConfiguration.name
                }

                testDependenciesConfigurationName.set(customIntellijPlatformTestDependenciesConfiguration.name)

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
                        intellijPlatformClasspathConfiguration = customIntellijPlatformClasspathConfiguration
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
        private val dependenciesHelper: IntelliJPlatformDependenciesHelper,
        private val taskClass: Class<T>,
    ) : IntelliJPlatformDependencyConfiguration(project.objects, project.extensionProvider), Named, ExtensionAware, Buildable {

        abstract val sandboxDirectory: DirectoryProperty
        abstract val localPath: DirectoryProperty
        abstract val splitMode: Property<Boolean>
        abstract val splitModeTarget: Property<SplitModeTarget>

        internal val testDependenciesConfigurationName: Property<String> = project.objects.property(String::class.java)

        fun plugins(configuration: Action<IntelliJPlatformPluginsExtension>) {
            configuration.execute(the<IntelliJPlatformPluginsExtension>())
        }

        /**
         * Adds a dependency on the `test-framework` library or its variant, required for testing plugins.
         *
         * There are multiple Test Framework variants available, which provide additional classes for testing specific modules, like:
         * JUnit4, JUnit 5, Maven, JavaScript, Go, Java, ReSharper, etc.
         *
         * The version, if absent, is determined by the IntelliJ Platform build number.
         *
         * @param type Test framework variant type.
         * @param version Test framework library version.
         * @see TestFrameworkType
         */
        @JvmOverloads
        fun testFramework(
            type: TestFrameworkType,
            version: String = Constraints.CLOSEST_VERSION,
        ) = dependenciesHelper.addTestFrameworkDependency(
            type = type,
            versionProvider = dependenciesHelper.provider { version },
            configurationName = testDependenciesConfigurationName.get(),
        )

        /**
         * Adds a dependency on the `test-framework` library required for testing plugins.
         *
         * There are multiple Test Framework variants available, which provide additional classes for testing specific modules, like:
         * JUnit4, JUnit 5, Maven, JavaScript, Go, Java, ReSharper, etc.
         *
         * The version, if absent, is determined by the IntelliJ Platform build number.
         *
         * @param type Test Framework variant type.
         * @param version Library version.
         * @see TestFrameworkType
         */
        fun testFramework(
            type: TestFrameworkType,
            version: Provider<String>,
        ) = dependenciesHelper.addTestFrameworkDependency(
            type = type,
            versionProvider = version,
            configurationName = testDependenciesConfigurationName.get(),
        )

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

    abstract class RunIdeParameters @Inject constructor(name: String, project: Project, dependenciesHelper: IntelliJPlatformDependenciesHelper) :
        CommonParameters<RunIdeTask>(name, project, dependenciesHelper, RunIdeTask::class.java)

    abstract class TestIdeParameters @Inject constructor(name: String, project: Project, dependenciesHelper: IntelliJPlatformDependenciesHelper) :
        CommonParameters<TestIdeTask>(name, project, dependenciesHelper, TestIdeTask::class.java)

    abstract class TestIdeUiParameters @Inject constructor(name: String, project: Project, dependenciesHelper: IntelliJPlatformDependenciesHelper) :
        CommonParameters<TestIdeUiTask>(name, project, dependenciesHelper, TestIdeUiTask::class.java)

    abstract class TestIdePerformanceParameters @Inject constructor(name: String, project: Project, dependenciesHelper: IntelliJPlatformDependenciesHelper) :
        CommonParameters<TestIdePerformanceTask>(name, project, dependenciesHelper, TestIdePerformanceTask::class.java)
}
