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
import org.gradle.kotlin.dsl.of
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.jetbrains.intellij.platform.gradle.Constants.GradleProperties
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.providers.CurrentPluginVersionValueSource
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.asPath
import kotlin.io.path.writeText

@CacheableTask
abstract class GenerateManifestTask : DefaultTask() {

    @get:Input
    abstract val gradlePluginVersion: Property<String>

    @get:Input
    abstract val gradleVersion: Property<String>

    @get:Input
    abstract val kotlinStdlibBundled: Property<Boolean>

    @get:Input
    abstract val kotlinVersion: Property<String>

    @get:Input
    abstract val productInfo: Property<ProductInfo>

    @get:Input
    abstract val version: Property<String>

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
            Build-Plugin-Version: ${gradlePluginVersion.get()}
            Platform-Type: ${productInfo.map { it.productCode.toIntelliJPlatformType() }.get()}
            Platform-Version: ${productInfo.map { it.version }.get()}
            Platform-Build: ${productInfo.map { it.buildNumber }.get()}
            Kotlin-Stdlib-Bundled: ${kotlinStdlibBundled.get()}
            Kotlin-Version: ${kotlinVersion.get()}
            """.trimIndent()
        )
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<GenerateManifestTask>(Tasks.GENERATE_MANIFEST) {
                val extension = project.the<IntelliJPlatformExtension>()

                productInfo.convention(project.provider { extension.productInfo })
                kotlinStdlibBundled.convention(
                    project.providers
                        .gradleProperty(GradleProperties.KOTLIN_STDLIB_DEFAULT_DEPENDENCY)
                        .map { it.toBoolean() }
                )
                kotlinVersion.convention(project.provider {
                    when {
                        project.pluginManager.hasPlugin(Plugins.External.KOTLIN) ->
                            project.extensions
                                .getByName("kotlin")
                                .withGroovyBuilder { getProperty("coreLibrariesVersion") as String }

                        else -> ""
                    }
                })

                gradlePluginVersion.convention(project.providers.of(CurrentPluginVersionValueSource::class) {})
                gradleVersion.convention(project.provider { project.gradle.gradleVersion })
                version.convention(project.provider { project.version.toString() })

                generatedManifest = temporaryDir.resolve("MANIFEST.MF")
            }
    }
}
