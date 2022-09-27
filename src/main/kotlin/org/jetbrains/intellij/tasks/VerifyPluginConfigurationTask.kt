// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.intellij.Version
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.parsePluginXml
import org.jetbrains.intellij.utils.PlatformJavaVersions
import org.jetbrains.intellij.utils.PlatformKotlinVersions
import org.jetbrains.intellij.warn
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

abstract class VerifyPluginConfigurationTask : DefaultTask() {

    /**
     * The location of the built plugin file which will be used for verification.
     *
     * Default value: `${prepareSandboxTask.destinationDir}/${prepareSandboxTask.pluginName}``
     */
    @get:InputFiles
    abstract val pluginXmlFiles: ListProperty<File>

    /**
     * IntelliJ SDK platform version.
     */
    @get:Internal
    abstract val platformVersion: Property<String>

    /**
     * IntelliJ SDK platform build number.
     */
    @get:Internal
    abstract val platformBuild: Property<String>

    /**
     * [JavaCompile.sourceCompatibility] property defined in the build script.
     */
    @get:Internal
    abstract val sourceCompatibility: Property<String>

    /**
     * [JavaCompile.targetCompatibility] property defined in the build script.
     */
    @get:Internal
    abstract val targetCompatibility: Property<String>

    /**
     * The Gradle Kotlin plugin is loaded.
     */
    @get:Internal
    abstract val kotlinPluginAvailable: Property<Boolean>

    /**
     * [KotlinCompile.kotlinOptions.apiVersion] property defined in the build script
     */
    @get:Internal
    abstract val kotlinApiVersion: Property<String?>

    /**
     * [KotlinCompile.kotlinOptions.languageVersion] property defined in the build script
     */
    @get:Internal
    abstract val kotlinLanguageVersion: Property<String?>

    /**
     * [KotlinCompile.kotlinOptions.jvmTarget] property defined in the build script.
     */
    @get:Internal
    abstract val kotlinJvmTarget: Property<String?>

    /**
     * `kotlin.stdlib.default.dependency` property value defined in the `gradle.properties` file.
     */
    @get:Internal
    abstract val kotlinStdlibDefaultDependency: Property<Boolean>

    private val context = logCategory()

    @TaskAction
    fun verifyPlugin() {
        val platformVersion = platformVersion.get().let(Version::parse)
        val platformBuildVersion = platformBuild.get().let(Version::parse)
        val platformJavaVersion = platformBuildVersion.let(::getPlatformJavaVersion)
        val sourceCompatibilityJavaVersion = sourceCompatibility.get().let(JavaVersion::toVersion)
        val targetCompatibilityJavaVersion = targetCompatibility.get().let(JavaVersion::toVersion)
        val jvmTargetJavaVersion = kotlinJvmTarget.orNull?.let(JavaVersion::toVersion)
        val kotlinApiVersion = kotlinApiVersion.orNull?.let(Version::parse)
        val kotlinLanguageVersion = kotlinLanguageVersion.orNull?.let(Version::parse)
        val platformKotlinLanguageVersion = platformBuildVersion.let(::getPlatformKotlinVersion)

        (pluginXmlFiles.get().flatMap {
            parsePluginXml(it, context)?.let { plugin ->
                val sinceBuild = plugin.ideaVersion.sinceBuild.let(Version::parse)
                val sinceBuildJavaVersion = sinceBuild.let(::getPlatformJavaVersion)
                val sinceBuildKotlinApiVersion = sinceBuild.let(::getPlatformKotlinVersion)

                listOfNotNull(
                    "The 'since-build' property is lower than the target IntelliJ Platform major version: $sinceBuild < ${platformBuildVersion.major}.".takeIf {
                        sinceBuild.major < platformBuildVersion.major
                    },
                    "The Java configuration specifies targetCompatibility=$targetCompatibilityJavaVersion but since-build='$sinceBuild' property requires targetCompatibility=$sinceBuildJavaVersion.".takeIf {
                        sinceBuildJavaVersion < targetCompatibilityJavaVersion
                    },
                    "The Kotlin configuration specifies jvmTarget=$jvmTargetJavaVersion but since-build='$sinceBuild' property requires jvmTarget=$sinceBuildJavaVersion.".takeIf {
                        sinceBuildJavaVersion < jvmTargetJavaVersion
                    },
                    "The Kotlin configuration specifies apiVersion=$kotlinApiVersion but since-build='$sinceBuild' property requires apiVersion=$sinceBuildKotlinApiVersion.".takeIf {
                        sinceBuildKotlinApiVersion < kotlinApiVersion
                    },
                )
            } ?: emptyList()
        } + listOfNotNull(
            "The Java configuration specifies sourceCompatibility=$sourceCompatibilityJavaVersion but IntelliJ Platform $platformVersion requires sourceCompatibility=$platformJavaVersion.".takeIf {
                platformJavaVersion > sourceCompatibilityJavaVersion
            },
            "The Kotlin configuration specifies languageVersion=$kotlinLanguageVersion but IntelliJ Platform $platformVersion requires languageVersion=$platformKotlinLanguageVersion.".takeIf {
                platformKotlinLanguageVersion > kotlinLanguageVersion
            },
            "The Java configuration specifies targetCompatibility=$targetCompatibilityJavaVersion but IntelliJ Platform $platformVersion requires targetCompatibility=$platformJavaVersion.".takeIf {
                platformJavaVersion < targetCompatibilityJavaVersion
            },
            "The Kotlin configuration specifies jvmTarget=$jvmTargetJavaVersion but IntelliJ Platform $platformVersion requires jvmTarget=$platformJavaVersion.".takeIf {
                platformJavaVersion < jvmTargetJavaVersion
            },
            "The dependency on the Kotlin Standard Library (stdlib) is automatically added when using the Gradle Kotlin plugin and may conflict with the version provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-stdlib".takeIf {
                kotlinPluginAvailable.get() && kotlinStdlibDefaultDependency.orNull == null
            }
        )).takeIf(List<String>::isNotEmpty)?.let { issues ->
            warn(
                context,
                "The following plugin configuration issues were found:" +
                        "\n" +
                        issues.joinToString("\n") { "- $it" } +
                        "\n" +
                        "See: https://jb.gg/intellij-platform-versions"
            )
        }
    }

    private fun getPlatformJavaVersion(buildNumber: Version) = PlatformJavaVersions.entries.firstOrNull { buildNumber >= it.key }?.value

    private fun getPlatformKotlinVersion(buildNumber: Version) = PlatformKotlinVersions.entries.firstOrNull { buildNumber >= it.key }?.value

    private operator fun JavaVersion?.compareTo(other: JavaVersion?) = other?.let { this?.compareTo(it) } ?: 0
    private operator fun Version?.compareTo(other: Version?) = other?.let { this?.compareTo(it) } ?: 0
}
