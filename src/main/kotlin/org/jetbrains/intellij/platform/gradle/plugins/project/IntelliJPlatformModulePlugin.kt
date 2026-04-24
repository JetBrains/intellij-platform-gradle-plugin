// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.*
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.attributes.ComposedJarRule
import org.jetbrains.intellij.platform.gradle.attributes.DistributionRule
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.services.IntelliJPlatformProjectsService
import org.jetbrains.intellij.platform.gradle.services.RequestedIntelliJPlatformsService
import org.jetbrains.intellij.platform.gradle.services.registerClassLoaderScopedBuildService
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.companion.JarCompanion
import org.jetbrains.intellij.platform.gradle.tasks.companion.TestCompanion
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.create
import org.jetbrains.intellij.platform.gradle.utils.dependenciesHelper
import java.util.concurrent.ConcurrentHashMap

abstract class IntelliJPlatformModulePlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    companion object {
        private const val ROOT_PROJECT_PATH = ":"
    }

    override fun apply(project: Project) {
        log.info("Configuring plugin: ${Plugins.MODULE}")

        val dependenciesHelper = project.dependenciesHelper

        with(project.plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        dependenciesHelper.registerPlatformPathProvider()

        val intellijPlatformProjects = project.gradle
            .registerClassLoaderScopedBuildService(IntelliJPlatformProjectsService::class)
            .get()
            .also { it.markModuleProject(project.path) }

        // To understand what is going on below read & watch this:
        // https://youtu.be/2gPJD0mAres?t=461
        // https://www.youtube.com/watch?v=8z5KFCLZDd0
        // https://docs.gradle.org/current/userguide/variant_attributes.html#sec:standard_attributes
        // https://docs.gradle.org/current/userguide/cross_project_publications.html#sec:variant-aware-sharing
        val compileJavaTaskProvider = project.tasks.named<JavaCompile>(Tasks.External.COMPILE_JAVA)
        with(project.configurations) configurations@{
            fun Configuration.applyVariantCommonAttributes(block: AttributeContainer.() -> Unit = {}) {
                attributes {
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.EXTERNAL))
                    attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))

                    // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1772
                    // https://docs.gradle.org/current/userguide/cross_project_publications.html#targeting-different-platforms
                    // > By default, the org.gradle.jvm.version is set to the value of the release property
                    // > (or as fallback to the targetCompatibility value) of the main compilation task of the source set.
                    attributeProvider(
                        TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                        compileJavaTaskProvider.map { task ->
                            task.options.release.orNull ?: JavaVersion.toVersion(task.targetCompatibility).majorVersion.toInt()
                        }
                    )

                    attributes.attribute(Attributes.jvmEnvironment, "standard-jvm")
                    attributes.attribute(Attributes.kotlinJPlatformType, "jvm")

                    block()
                }
            }

            create(
                name = Configurations.INTELLIJ_PLATFORM_COMPOSED_JAR_API,
                description = "IntelliJ Platform final composed Jar archive Api",
            ) {
                /**
                 * Setting both flags to `true` is deprecated:
                 *
                 * > For backwards compatibility, both flags have a default value of true, but as a plugin author,
                 * > you should always determine the right values for those flags, or you might accidentally introduce resolution errors.
                 *
                 * See: https://docs.gradle.org/current/userguide/declaring_dependencies_adv.html#sec:resolvable-consumable-configs
                 */
                isCanBeConsumed = true
                isCanBeResolved = false

                // TODO: a possible fix for #1892
                // applyVariantCommonAttributes {
                //     attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_API))
                //     attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(Attributes.COMPOSED_JAR_NAME))
                // }

                /**
                 * A separate consumable [Configurations.INTELLIJ_PLATFORM_COMPOSED_JAR_API] configuration is necessary so that we can register
                 * a separate outgoing variant with `attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_API))` so that custom
                 * configurations `api` & `compileOnlyApi` created by the `java-library` plugin can actually work.
                 * See: https://docs.gradle.org/current/userguide/java_library_plugin.html
                 *
                 * We can't extend here from `apiElements` because then we will inherit its `-base.jar` registered
                 * by the java plugin by default, which we don't want.
                 */
                extendsFrom(
                    this@configurations[Configurations.External.API],
                    this@configurations[Configurations.External.COMPILE_ONLY_API]
                )
            }

            create(
                name = Configurations.INTELLIJ_PLATFORM_COMPOSED_JAR,
                description = "IntelliJ Platform final composed Jar archive",
            ) {
                isCanBeConsumed = true
                isCanBeResolved = true

                applyVariantCommonAttributes {
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(Attributes.COMPOSED_JAR_NAME))
                }

                /**
                 * We can't extend here from [Configurations.External.RUNTIME_ELEMENTS] because then
                 * we will inherit its `-base.jar` registered by the java plugin by default, which we don't want.
                 */
                extendsFrom(
                    this@configurations[Configurations.External.IMPLEMENTATION],
                    this@configurations[Configurations.External.RUNTIME_ONLY],
                )
            }
            create(
                name = Configurations.INTELLIJ_PLATFORM_DISTRIBUTION,
                description = "IntelliJ Platform distribution Zip archive",
            ) {
                /**
                 * Setting both flags to `true` is deprecated:
                 *
                 * > For backwards compatibility, both flags have a default value of true, but as a plugin author,
                 * > you should always determine the right values for those flags, or you might accidentally introduce resolution errors.
                 *
                 * See: https://docs.gradle.org/current/userguide/declaring_dependencies_adv.html#sec:resolvable-consumable-configs
                 */
                isCanBeConsumed = true
                isCanBeResolved = false

                applyVariantCommonAttributes {
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
                }
            }

            create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE,
                description = "IntelliJ Platform plugin module",
            ) { isTransitive = false }

            create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN_COMPOSED_MODULE,
                description = "IntelliJ Platform plugin composed module",
            ) { isTransitive = false }

            listOf(
                Configurations.INTELLIJ_PLATFORM_TEST_CLASSPATH,
                Configurations.INTELLIJ_PLATFORM_TEST_RUNTIME_CLASSPATH,
                Configurations.INTELLIJ_PLATFORM_RUNTIME_CLASSPATH,
                Configurations.External.COMPILE_CLASSPATH,
                Configurations.External.TEST_COMPILE_CLASSPATH,
                Configurations.External.TEST_RUNTIME_CLASSPATH,
                // TODO: required for test fixtures?
                //       Configurations.External.TEST_FIXTURES_COMPILE_CLASSPATH,
                Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE,
                Configurations.INTELLIJ_PLATFORM_PLUGIN_COMPOSED_MODULE,
            ).forEach {
                named(it) {
                    attributes {
                        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(Attributes.COMPOSED_JAR_NAME))
                    }
                }
            }

            listOf(
                Configurations.INTELLIJ_PLATFORM_DISTRIBUTION,
                Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL,
                Configurations.INTELLIJ_PLATFORM_PLUGIN,
            ).forEach {
                named(it) {
                    attributes {
                        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(Attributes.DISTRIBUTION_NAME))
                    }
                }
            }
        }

        with(project.dependencies) {
            attributesSchema {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE) {
                    compatibilityRules.add(ComposedJarRule::class)
                    compatibilityRules.add(DistributionRule::class)
                }
            }
        }

        if (project.path != ROOT_PROJECT_PATH) {
            val rootRequestedIntelliJPlatforms = project.gradle
                .registerClassLoaderScopedBuildService(RequestedIntelliJPlatformsService::class, ROOT_PROJECT_PATH) {
                    parameters.useInstaller = true
                }
                .get()

            dependenciesHelper.addIntelliJPlatformLocalDependency(
                localPathProvider = project.provider {
                    when {
                        dependenciesHelper.hasExplicitIntelliJPlatformDependency() -> null
                        !intellijPlatformProjects.isPluginProject(ROOT_PROJECT_PATH) -> null
                        !rootRequestedIntelliJPlatforms.hasExplicit() -> null
                        else -> intellijPlatformProjects.getPlatformPathProvider(ROOT_PROJECT_PATH)?.orNull?.toFile()
                    }
                },
                isExplicit = false,
            )
        }

        val intellijPlatformPluginModuleConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE]
        val intellijPlatformPluginComposedModuleConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_COMPOSED_MODULE]
        val pluginModuleDependencyPaths = ConcurrentHashMap.newKeySet<String>()
        intellijPlatformPluginModuleConfiguration.dependencies.whenObjectAdded(Unit.closureOf<Dependency> {
            if (this is ProjectDependency) {
                pluginModuleDependencyPaths += path
            }
        })
        val pluginComposedModuleDependencyPaths = ConcurrentHashMap.newKeySet<String>()
        intellijPlatformPluginComposedModuleConfiguration.dependencies.whenObjectAdded(Unit.closureOf<Dependency> {
            if (this is ProjectDependency) {
                pluginComposedModuleDependencyPaths += path
            }
        })

        val inferredPluginModuleDependencies = ConcurrentHashMap<String, ProjectDependency>()
        fun addInferredPluginModuleDependency(dependency: ProjectDependency) {
            inferredPluginModuleDependencies.putIfAbsent(dependency.path, dependency)
        }

        listOf(
            Configurations.External.API,
            Configurations.External.IMPLEMENTATION,
            Configurations.External.RUNTIME_ONLY,
        ).forEach { configurationName ->
            project.configurations[configurationName].dependencies
                .withType<ProjectDependency>()
                .all(::addInferredPluginModuleDependency)
        }

        intellijPlatformPluginModuleConfiguration.dependencies.addAllLater(project.provider {
            inferredPluginModuleDependencies.values
                .asSequence()
                .filterNot { it.path in pluginModuleDependencyPaths }
                .filterNot { it.path in pluginComposedModuleDependencyPaths }
                .filter { intellijPlatformProjects.isPureModuleProject(it.path) }
                .sortedBy { it.path }
                .toList()
        })

        IntelliJPlatformTestingExtension.register(
            project = project,
            dependenciesHelper = dependenciesHelper,
            target = project,
        )

        listOf(
            // Build Module
            GenerateManifestTask,
            JarCompanion,
            InstrumentCodeTask,
            InstrumentedJarTask,
            ComposedJarTask,
            PrepareSandboxTask,
            CleanSandboxTask,

            // Test Module
            PrepareTestTask,
            TestCompanion,
            TestIdeTask,
            TestIdeUiTask,

            // Verify Module
            VerifyPluginProjectConfigurationTask,
        ).forEach {
            it.register(project)
        }
    }
}
