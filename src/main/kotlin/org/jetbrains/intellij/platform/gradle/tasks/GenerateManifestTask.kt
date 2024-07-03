// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.tasks.aware.KotlinMetadataAware
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import kotlin.io.path.writeText

@CacheableTask
abstract class GenerateManifestTask : DefaultTask(), KotlinMetadataAware {

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
            Kotlin-Stdlib-Bundled: ${kotlinPluginAvailable.get() && kotlinStdlibDefaultDependency.orNull != false}
            Kotlin-Version: ${kotlinVersion.orNull}
            """.trimIndent()
        )
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Generates the MANIFEST.MF file."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<GenerateManifestTask>(Tasks.GENERATE_MANIFEST) {
                val initializeIntelliJPlatformPluginTaskProvider =
                    project.tasks.named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)

                productInfo.convention(project.extensionProvider.map { it.productInfo })
                pluginVersion.convention(initializeIntelliJPlatformPluginTaskProvider.flatMap { it.pluginVersion })
                gradleVersion.convention(project.provider { project.gradle.gradleVersion })
                version.convention(project.extensionProvider.flatMap { it.pluginConfiguration.version })

                generatedManifest = temporaryDir.resolve("MANIFEST.MF")
            }
    }
}
