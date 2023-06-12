// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.create
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.intellij.*
import org.jetbrains.intellij.IntelliJPluginConstants.GITHUB_REPOSITORY
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_ID
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_NAME
import org.jetbrains.intellij.utils.LatestVersionResolver
import java.io.File

/**
 * Initializes the IntelliJ Platform Gradle Plugin and performs various checks, like if the plugin is up to date.
 */
@UntrackedTask(because = "Should always be run to initialize the plugin")
abstract class InitializeIntelliJPluginTask : DefaultTask() {

    @get:Internal
    abstract val offline: Property<Boolean>

    @get:Internal
    abstract val selfUpdateCheck: Property<Boolean>

    @get:Internal
    abstract val lockFile: Property<File>

    init {
        group = PLUGIN_GROUP_NAME
        description = "Initializes the IntelliJ Platform Gradle Plugin"
    }

    private val context = logCategory()

    @TaskAction
    fun initialize() {
        checkPluginVersion()
    }

    /**
     * Checks if the plugin is up to date.
     */
    private fun checkPluginVersion() {
        if (!selfUpdateCheck.get() || offline.get()) {
            return
        }

        try {
            val version = getCurrentPluginVersion()
                ?.let(Version::parse)
                .or { Version() }
            val latestVersion = LatestVersionResolver.fromGitHub(PLUGIN_NAME, GITHUB_REPOSITORY)
            if (version < Version.parse(latestVersion)) {
                warn(context, "$PLUGIN_NAME is outdated: $version. Update `$PLUGIN_ID` to: $latestVersion")
            }

            lockFile.get().toPath().create()
        } catch (e: Exception) {
            error(context, e.message.orEmpty(), e)
        }
    }
}
