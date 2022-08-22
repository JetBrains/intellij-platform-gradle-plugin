// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.JavaVersion
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.Version
import org.jetbrains.intellij.error
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.parsePluginXml
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import javax.inject.Inject

open class VerifyPluginConfigurationTask @Inject constructor(
    objectFactory: ObjectFactory,
) : ConventionTask() {

    /**
     * The location of the built plugin file which will be used for verification.
     *
     * Default value: `${prepareSandboxTask.destinationDir}/${prepareSandboxTask.pluginName}``
     */
    @InputFiles
    val pluginXmlFiles = objectFactory.listProperty<File>()

    /**
     * IntelliJ SDK platform version.
     */
    @Internal
    val platformVersion = objectFactory.property<String>()

    /**
     * IntelliJ SDK platform build number.
     */
    @Internal
    val platformBuild = objectFactory.property<String>()

    /**
     * [JavaCompile.sourceCompatibility] property defined in the build script.
     */
    @Internal
    val sourceCompatibility = objectFactory.property<String>()

    /**
     * [JavaCompile.targetCompatibility] property defined in the build script.
     */
    @Internal
    val targetCompatibility = objectFactory.property<String>()

    /**
     * [KotlinCompile.kotlinOptions] property defined in the build script.
     */
    @Internal
    val jvmTarget = objectFactory.property<String>()

    private val context = logCategory()

    @TaskAction
    fun verifyPlugin() {
        val platformVersion = platformVersion.get().let(Version::parse)
        val platformBuildVersion = platformBuild.get().let(Version::parse)
        val platformJavaVersion = platformBuildVersion.let(::getPlatformJavaVersion)
        val sourceCompatibilityJavaVersion = sourceCompatibility.get().let(JavaVersion::toVersion)
        val targetCompatibilityJavaVersion = targetCompatibility.get().let(JavaVersion::toVersion)
        val jvmTargetJavaVersion = jvmTarget.orNull?.let(JavaVersion::toVersion)

        (pluginXmlFiles.get().flatMap {
            parsePluginXml(it, context)?.let { plugin ->
                val sinceBuild = plugin.ideaVersion.sinceBuild.let(Version::parse)
                val sinceBuildJavaVersion = sinceBuild.let(::getPlatformJavaVersion)

                println("sinceBuild='${sinceBuild}'")

                println("sinceBuildJavaVersion='${sinceBuildJavaVersion}'")
                println("targetCompatibilityJavaVersion='${targetCompatibilityJavaVersion}'")
                println("sinceBuildJavaVersion < targetCompatibilityJavaVersion='${sinceBuildJavaVersion < targetCompatibilityJavaVersion}'")

                listOfNotNull(
                    "The 'since-build' property is lower than the target IntelliJ Platform major version: $sinceBuild < ${platformBuildVersion.major}.".takeIf {
                        (sinceBuild.major < platformBuildVersion.major)
                    },
                    "The Java configuration specifies targetCompatibility=$targetCompatibilityJavaVersion but since-build='$sinceBuild' property requires targetCompatibility=$sinceBuildJavaVersion.".takeIf {
                        sinceBuildJavaVersion < targetCompatibilityJavaVersion
                    },
                    "The Kotlin configuration specifies jvmTarget=$jvmTargetJavaVersion but since-build='$sinceBuild' property requires jvmTarget=$sinceBuildJavaVersion.".takeIf {
                        jvmTargetJavaVersion != null && sinceBuildJavaVersion < jvmTargetJavaVersion
                    },
                )
            } ?: emptyList()
        } + listOfNotNull(
            "The Java configuration specifies sourceCompatibility=$sourceCompatibilityJavaVersion but IntelliJ Platform $platformVersion requires sourceCompatibility=$platformJavaVersion.".takeIf {
                platformJavaVersion > sourceCompatibilityJavaVersion
            },
            "The Java configuration specifies targetCompatibility=$targetCompatibilityJavaVersion but IntelliJ Platform $platformVersion requires targetCompatibility=$platformJavaVersion.".takeIf {
                platformJavaVersion < targetCompatibilityJavaVersion
            },
            "The Kotlin configuration specifies jvmTarget=$jvmTargetJavaVersion but IntelliJ Platform $platformVersion requires jvmTarget=$platformJavaVersion.".takeIf {
                jvmTargetJavaVersion != null && platformJavaVersion < jvmTargetJavaVersion
            },
        )).takeIf(List<String>::isNotEmpty)?.let { issues ->
            error(
                context,
                "The following compatibility configuration issues were found:" +
                    "\n" +
                    issues.joinToString("\n") { "- $it" } +
                    "\n" +
                    "See: https://jb.gg/intellij-platform-versions"
            )
        }
    }

    private fun getPlatformJavaVersion(buildNumber: Version) = when {
        buildNumber >= Version.parse("222") -> JavaVersion.VERSION_17
        buildNumber >= Version.parse("203") -> JavaVersion.VERSION_11
        else -> JavaVersion.VERSION_1_8
    }
}
