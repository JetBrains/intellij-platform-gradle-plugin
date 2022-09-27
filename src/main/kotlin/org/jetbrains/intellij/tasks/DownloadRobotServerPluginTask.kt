// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.newInstance
import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginConstants.INTELLIJ_DEPENDENCIES
import org.jetbrains.intellij.Version
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.utils.ArchiveUtils
import org.jetbrains.intellij.utils.LatestVersionResolver
import java.io.File
import javax.inject.Inject

abstract class DownloadRobotServerPluginTask @Inject constructor(
    objectFactory: ObjectFactory
) : DefaultTask() {

    companion object {
        private const val METADATA_URL = "$INTELLIJ_DEPENDENCIES/com/intellij/remoterobot/robot-server-plugin/maven-metadata.xml"
        private const val OLD_ROBOT_SERVER_DEPENDENCY = "org.jetbrains.test:robot-server-plugin"
        private const val NEW_ROBOT_SERVER_DEPENDENCY = "com.intellij.remoterobot:robot-server-plugin"
        private const val NEW_ROBOT_SERVER_VERSION = "0.11.0"

        fun resolveLatestVersion() = LatestVersionResolver.fromMaven("Robot Server Plugin", METADATA_URL)
    }

    /**
     * The version of the Robot Server Plugin to download.
     *
     * Default value: `LATEST`
     */
    @get:Input
    abstract val version: Property<String>

    /**
     * The archive with the Robot Server Plugin, by default downloaded by to the Maven cache.
     *
     * Default value: Maven cache
     */
    @get:Input
    abstract val pluginArchive: Property<File>

    /**
     * Location of the extracted archive.
     *
     * Default value: `build/robotServerPlugin`
     */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val archiveUtils = objectFactory.newInstance<ArchiveUtils>()

    private val context = logCategory()

    @TaskAction
    fun downloadPlugin() {
        val target = outputDir.get().asFile
        archiveUtils.extract(pluginArchive.get(), target, context)
    }

    /**
     * Resolves the Robot Server version.
     * If set to [org.jetbrains.intellij.IntelliJPluginConstants.VERSION_LATEST], there's request to [METADATA_URL]
     * performed for the latest available version.
     *
     * @return Robot Server version
     */
    internal fun resolveRobotServerPluginVersion(version: String?) =
        version?.takeIf { it != IntelliJPluginConstants.VERSION_LATEST } ?: resolveLatestVersion()

    internal fun getDependency(version: String) = when {
        Version.parse(version) >= Version.parse(NEW_ROBOT_SERVER_VERSION) -> NEW_ROBOT_SERVER_DEPENDENCY
        else -> OLD_ROBOT_SERVER_DEPENDENCY
    }
}
