// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.attributes.ComposedJarRule
import org.jetbrains.intellij.platform.gradle.attributes.DistributionRule
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.companion.JarCompanion
import org.jetbrains.intellij.platform.gradle.tasks.companion.TestCompanion
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.create

@Suppress("unused")
abstract class IntelliJPlatformModulePlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: ${Plugins.MODULE}")

        with(project.plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        with(project.configurations) configurations@{
            fun Configuration.applyVariantAttributes() {
                attributes {
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.EXTERNAL))
                    attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
                    attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, project.provider {
                        project.the<JavaPluginExtension>().targetCompatibility.majorVersion.toInt()
                    })
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
                    attributes.attribute(Attributes.jvmEnvironment, "standard-jvm")
                    attributes.attribute(Attributes.kotlinJPlatformType, "jvm")
                }
            }

            create(
                name = Configurations.INTELLIJ_PLATFORM_COMPOSED_JAR,
                description = "IntelliJ Platform final composed Jar archive",
            ) {
                isCanBeConsumed = true
                isCanBeResolved = true

                applyVariantAttributes()

                extendsFrom(
                    this@configurations[Configurations.External.IMPLEMENTATION],
                    this@configurations[Configurations.External.RUNTIME_ONLY],
                )
            }
            create(
                name = Configurations.INTELLIJ_PLATFORM_DISTRIBUTION,
                description = "IntelliJ Platform distribution Zip archive",
            ) {
                isCanBeConsumed = true
                isCanBeResolved = false

                applyVariantAttributes()
            }

            val intellijPlatformPluginModuleConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE,
                description = "IntelliJ Platform plugin module",
            ) { isTransitive = false }

            named(Configurations.INTELLIJ_PLATFORM_DEPENDENCIES) {
                extendsFrom(intellijPlatformPluginModuleConfiguration)
            }


            listOf(
                Configurations.INTELLIJ_PLATFORM_COMPOSED_JAR,
                Configurations.INTELLIJ_PLATFORM_TEST_CLASSPATH,
                Configurations.INTELLIJ_PLATFORM_RUNTIME_CLASSPATH,
                Configurations.External.COMPILE_CLASSPATH,
                Configurations.External.TEST_COMPILE_CLASSPATH,
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

        IntelliJPlatformTestingExtension.register(project, target = project)

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
