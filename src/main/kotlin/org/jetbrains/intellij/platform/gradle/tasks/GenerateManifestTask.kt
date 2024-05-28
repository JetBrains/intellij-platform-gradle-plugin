// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.asPath
import kotlin.io.path.writeText

@CacheableTask
abstract class GenerateManifestTask : DefaultTask() {

    /**
     * The IntelliJ Platform Gradle Plugin version.
     */
    @get:Input
    abstract val pluginVersion: Property<String>

    /**
     * The version of currently used Gradle.
     */
    @get:Input
    abstract val gradleVersion: Property<String>

    /**
     * Indicates that the Kotlin Gradle Plugin is loaded and available.
     */
    @get:Internal
    abstract val kotlinPluginAvailable: Property<Boolean>

    /**
     * `kotlin.stdlib.default.dependency` property value defined in the `gradle.properties` file.
     */
    @get:Input
    abstract val kotlinStdlibDefaultDependency: Property<Boolean>

    /**
     * The version of Kotlin used in the project.
     */
    @get:Input
    abstract val kotlinVersion: Property<String?>

    /**
     * The [ProductInfo] instance of the current IntelliJ Platform.
     */
    @get:Input
    abstract val productInfo: Property<ProductInfo>

    /**
     * Plugin version.
     */
    @get:Input
    abstract val version: Property<String>

    /**
     * Location of the generated `MANIFEST.MF` file.
     */
    @get:OutputFile
    abstract val generatedManifest: RegularFileProperty

    @TaskAction
    fun generate() {
        generatedManifest.asPath.writeText(
            """
            Created-By: ${gradleVersion.map { "Gradle $it" }.get()}
            Version: ${version.get()}
            Build-JVM: ${Jvm.current()}
            Build-OS: ${OperatingSystem.current()}
            Build-Plugin: ${Plugin.NAME}
            Build-Plugin-Version: ${pluginVersion.get()}
            Platform-Type: ${productInfo.map { it.productCode.toIntelliJPlatformType() }.get()}
            Platform-Version: ${productInfo.map { it.version }.get()}
            Platform-Build: ${productInfo.map { it.buildNumber }.get()}
            Kotlin-Available: ${kotlinPluginAvailable.get()}
            Kotlin-Stdlib-Bundled: ${kotlinPluginAvailable.get() && kotlinStdlibDefaultDependency.get()}
            Kotlin-Version: ${kotlinVersion.orNull}
            """.trimIndent()
        )
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<GenerateManifestTask>(Tasks.GENERATE_MANIFEST) {
                val extension = project.the<IntelliJPlatformExtension>()
                val initializeIntelliJPlatformPluginTaskProvider =
                    project.tasks.named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
                val verifyPluginProjectConfigurationTaskProvider =
                    project.tasks.named<VerifyPluginProjectConfigurationTask>(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION)

                productInfo.convention(project.provider { extension.productInfo })
                kotlinStdlibDefaultDependency.convention(verifyPluginProjectConfigurationTaskProvider.flatMap { it.kotlinStdlibDefaultDependency })
                kotlinVersion.convention(verifyPluginProjectConfigurationTaskProvider.flatMap { it.kotlinVersion })
                pluginVersion.convention(initializeIntelliJPlatformPluginTaskProvider.flatMap { it.pluginVersion })
                gradleVersion.convention(project.provider { project.gradle.gradleVersion })
                version.convention(extension.pluginConfiguration.version)

                generatedManifest = temporaryDir.resolve("MANIFEST.MF")
            }
    }
}
