// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.*
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Extensions
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.JAVA_TEST_FIXTURES_PLUGIN_ID
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_BASE_ID
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Sandbox
import org.jetbrains.intellij.platform.gradle.artifacts.transform.applyCollectorTransformer
import org.jetbrains.intellij.platform.gradle.artifacts.transform.applyExtractorTransformer
import org.jetbrains.intellij.platform.gradle.artifacts.transform.applyProductInfoTransformer
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension

abstract class IntelliJPlatformBasePlugin : IntelliJPlatformAbstractProjectPlugin(PLUGIN_BASE_ID) {

    override fun Project.configure() {

        with(plugins) {
            apply(JavaPlugin::class)
        }

        with(configurations) {
            val intellijPlatformDependencyConfiguration = maybeCreate(Configurations.INTELLIJ_PLATFORM_DEPENDENCY)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "IntelliJ Platform dependency archive"
                }

            val intellijPlatformLocalConfiguration = maybeCreate(Configurations.INTELLIJ_PLATFORM_LOCAL_INSTANCE)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "IntelliJ Platform local instance"
                }

            val intellijPlatformConfiguration = maybeCreate(Configurations.INTELLIJ_PLATFORM)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "IntelliJ Platform"

                    attributes {
                        attribute(Attributes.extracted, true)
                    }

                    extendsFrom(intellijPlatformDependencyConfiguration, intellijPlatformLocalConfiguration)

                    incoming.beforeResolve {
                        if (dependencies.isEmpty()) {
                            throw GradleException("No IntelliJ Platform dependency found")
                        }

                        val identifiers = IntelliJPlatformType.values().map { "${it.groupId}:${it.artifactId}" }
                        val matched = dependencies.filter { identifiers.contains("${it.group}:${it.name}") }
                        if (matched.size > 1) {
                            throw GradleException("Conflicting dependencies detected: \n${matched.joinToString("\n")}")
                        }
                    }
                }

            maybeCreate(Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "IntelliJ Platform product info"

                    attributes {
                        attribute(Attributes.productInfo, true)
                    }

                    extendsFrom(intellijPlatformConfiguration)
                }

            val jetbrainsRuntimeDependencyConfiguration = maybeCreate(Configurations.JETBRAINS_RUNTIME_DEPENDENCY)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "JetBrains Runtime dependency archive"

                    attributes {
                        attribute(Attributes.extracted, false)
                    }
                }

            val jetbrainsRuntimeLocalConfiguration = maybeCreate(Configurations.JETBRAINS_RUNTIME_LOCAL_INSTANCE)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "JetBrains Runtime local instance"

                    attributes {
                        attribute(Attributes.extracted, true)
                    }
                }

            maybeCreate(Configurations.JETBRAINS_RUNTIME)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "JetBrains Runtime"

                    attributes {
                        attribute(Attributes.extracted, true)
                    }

                    extendsFrom(jetbrainsRuntimeDependencyConfiguration)
                    extendsFrom(jetbrainsRuntimeLocalConfiguration)
                }

            val intellijPlatformDependenciesConfiguration = maybeCreate(Configurations.INTELLIJ_PLATFORM_DEPENDENCIES)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "IntelliJ Platform extra dependencies"
                }

            fun Configuration.extend() = extendsFrom(
                intellijPlatformConfiguration,
                intellijPlatformDependenciesConfiguration,
            )

            getByName(COMPILE_ONLY_CONFIGURATION_NAME).extend()
            getByName(TEST_COMPILE_ONLY_CONFIGURATION_NAME).extend()
            pluginManager.withPlugin(JAVA_TEST_FIXTURES_PLUGIN_ID) {
                getByName(Configurations.TEST_FIXTURES_COMPILE_ONLY).extend()
            }
        }

        with(dependencies) {
//            applyIntelliJPlatformSettings(objects, gradle)

            attributesSchema {
                attribute(Attributes.collected)
                attribute(Attributes.extracted)
                attribute(Attributes.productInfo)
            }

            applyExtractorTransformer(
                configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME),
                configurations.getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME),
                configurations.getByName(Configurations.INTELLIJ_PLATFORM_DEPENDENCY),
                configurations.getByName(Configurations.JETBRAINS_RUNTIME_DEPENDENCY),
            )
            applyCollectorTransformer(
                configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME),
                configurations.getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME),
            )
            applyProductInfoTransformer()
        }

        configureExtension<IntelliJPlatformExtension>(Extensions.INTELLIJ_PLATFORM) {
            instrumentCode.convention(true)
            sandboxContainer.convention(project.layout.buildDirectory.dir(Sandbox.CONTAINER))

            configureExtension<IntelliJPlatformExtension.PluginConfiguration>(Extensions.PLUGIN_CONFIGURATION) {
                name.convention(project.name)
                version.convention(this@configure.version.toString())

                configureExtension<IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor>(Extensions.PRODUCT_DESCRIPTOR)
                configureExtension<IntelliJPlatformExtension.PluginConfiguration.IdeaVersion>(Extensions.IDEA_VERSION)
                configureExtension<IntelliJPlatformExtension.PluginConfiguration.Vendor>(Extensions.VENDOR)
            }
        }

        dependencies.configureExtension<IntelliJPlatformDependenciesExtension>(Extensions.INTELLIJ_PLATFORM, repositories, dependencies, providers, gradle)
        repositories.configureExtension<IntelliJPlatformRepositoriesExtension>(Extensions.INTELLIJ_PLATFORM, repositories, providers)
    }
}
