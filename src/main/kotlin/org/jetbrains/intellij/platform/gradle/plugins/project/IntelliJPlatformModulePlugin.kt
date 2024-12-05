// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.*
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.attributes.ComposedJarRule
import org.jetbrains.intellij.platform.gradle.attributes.DistributionRule
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesHelper
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.companion.JarCompanion
import org.jetbrains.intellij.platform.gradle.tasks.companion.TestCompanion
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.create
import org.jetbrains.intellij.platform.gradle.utils.rootProjectPath
import org.jetbrains.intellij.platform.gradle.utils.settings

abstract class IntelliJPlatformModulePlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: ${Plugins.MODULE}")

        with(project.plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        // To understand what is going on below read & watch this:
        // https://youtu.be/2gPJD0mAres?t=461
        // https://www.youtube.com/watch?v=8z5KFCLZDd0
        // https://docs.gradle.org/current/userguide/variant_attributes.html#sec:standard_attributes
        // https://docs.gradle.org/current/userguide/cross_project_publications.html#sec:variant-aware-sharing
        with(project.configurations) configurations@{
            fun Configuration.applyVariantCommonAttributes(block: AttributeContainer.() -> Unit = {}) {
                attributes {
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.EXTERNAL))
                    attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))

                    // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1772
                    // https://docs.gradle.org/current/userguide/cross_project_publications.html#targeting-different-platforms
                    // > By default, the org.gradle.jvm.version is set to the value of the release property
                    // > (or as fallback to the targetCompatibility value) of the main compilation task of the source set.
                    attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, project.provider {
                        project.the<JavaPluginExtension>().targetCompatibility.majorVersion.toInt()
                    })

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

                applyVariantCommonAttributes {
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_API))
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(Attributes.COMPOSED_JAR_NAME))
                }

                /**
                 * A separate consumable [Configurations.INTELLIJ_PLATFORM_COMPOSED_JAR_API] configuration is necessary so that we can register
                 * a separate outgoing variant with `attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_API))` so that custom
                 * configurations `api` & `compileOnlyApi` created by the `java-library` plugin can actually work.
                 * See: https://docs.gradle.org/current/userguide/java_library_plugin.html
                 *
                 * We can't extend here from [Configurations.External.API_ELEMENTS] because then
                 * we will inherit its `-base.jar` registered by the java plugin by default, which we don't want.
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

            val intellijPlatformPluginModuleConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE,
                description = "IntelliJ Platform plugin module",
            ) { isTransitive = false }

            named(Configurations.INTELLIJ_PLATFORM_DEPENDENCIES) {
                extendsFrom(intellijPlatformPluginModuleConfiguration)
            }

            listOf(
                Configurations.INTELLIJ_PLATFORM_TEST_CLASSPATH,
                Configurations.INTELLIJ_PLATFORM_RUNTIME_CLASSPATH,
                Configurations.External.COMPILE_CLASSPATH,
                Configurations.External.TEST_COMPILE_CLASSPATH,
                Configurations.External.TEST_RUNTIME_CLASSPATH,
                // TODO: required for test fixtures?
                //       Configurations.External.TEST_FIXTURES_COMPILE_CLASSPATH,
                Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE,
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

        // TODO: share with Base plugin?
        val dependenciesHelper = with(project) {
            IntelliJPlatformDependenciesHelper(configurations, dependencies, layout, objects, providers, resources, rootProjectPath, project.settings.dependencyResolutionManagement.rulesMode)
        }

        IntelliJPlatformTestingExtension.register(project, dependenciesHelper, target = project)

        listOf(
            // Build Module
            GenerateManifestTask,
            JarCompanion,
            InstrumentCodeTask,
            InstrumentedJarTask,
            ComposedJarTask,
            PrepareSandboxTask,

            // Test Module
            PrepareTestTask,
            TestCompanion,
            TestIdeTask,

            // Verify Module
            VerifyPluginProjectConfigurationTask,
        ).forEach {
            it.register(project)
        }
    }
}
