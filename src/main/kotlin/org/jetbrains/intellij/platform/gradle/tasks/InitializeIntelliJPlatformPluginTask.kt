// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_ID
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.base.PlatformVersionAware
import org.jetbrains.intellij.platform.gradle.utils.LatestVersionResolver
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.net.URL
import java.time.LocalDate
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.outputStream

/**
 * Initializes the IntelliJ Platform Gradle Plugin and performs various checks, like if the plugin is up-to-date.
 */
@UntrackedTask(because = "Should always be run to initialize the plugin")
abstract class InitializeIntelliJPlatformPluginTask : DefaultTask(), PlatformVersionAware {

    @get:Internal
    abstract val offline: Property<Boolean>

    @get:Internal
    abstract val selfUpdateCheck: Property<Boolean>

    @get:Internal
    abstract val selfUpdateLock: RegularFileProperty

    @get:Internal
    abstract val coroutinesJavaAgent: RegularFileProperty

    @get:Internal
    abstract val pluginVersion: Property<String>

    init {
        group = PLUGIN_GROUP_NAME
        description = "Initializes the IntelliJ Platform Gradle Plugin"
    }

    private val context = logCategory()

    @TaskAction
    fun initialize() {
        checkPluginVersion()
        createCoroutinesJavaAgentFile()
    }

    /**
     * Checks if the plugin is up-to-date.
     */
    private fun checkPluginVersion() {
        if (!selfUpdateCheck.get() || selfUpdateLock.asPath.exists() || offline.get()) {
            return
        }

        try {
            val version = Version.parse(pluginVersion.get())
            val latestVersion = LatestVersionResolver.plugin()
            if (version < Version.parse(latestVersion)) {
                warn(context, "$PLUGIN_NAME is outdated: $version. Update `$PLUGIN_ID` to: $latestVersion")
            }

            with(selfUpdateLock.asPath) {
                if (!exists()) {
                    parent.createDirectories()
                    createFile()
                }
            }
        } catch (e: Exception) {
            error(context, e.message.orEmpty(), e)
        }
    }

    /**
     * Creates a Java Agent file for the Coroutines library required to enable coroutines debugging.
     */
    private fun createCoroutinesJavaAgentFile() {
        if (coroutinesJavaAgent.asPath.exists()) {
            return
        }

        val manifest = Manifest(
            """
            Manifest-Version: 1.0
            Premain-Class: kotlinx.coroutines.debug.AgentPremain
            Can-Retransform-Classes: true
            Multi-Release: true
            
            """.trimIndent().byteInputStream()
        )

        JarOutputStream(coroutinesJavaAgent.asPath.outputStream(), manifest).close()
    }

    companion object {
        fun register(project: Project) =
            project.registerTask<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
                offline.convention(project.gradle.startParameter.isOffline)
                selfUpdateCheck.convention(project.isBuildFeatureEnabled(BuildFeature.SELF_UPDATE_CHECK))
                selfUpdateLock.convention(
                    project.layout.file(project.provider {
                        temporaryDir.resolve(LocalDate.now().toString())
                    })
                )
                coroutinesJavaAgent.convention(
                    project.layout.file(project.provider {
                        temporaryDir.resolve("coroutines-javaagent.jar")
                    })
                )
                pluginVersion.convention(project.provider {
                    InitializeIntelliJPlatformPluginTask::class.java
                        .run { getResource("$simpleName.class") }
                        .runCatching {
                            val manifestPath = with(this?.path) {
                                when {
                                    this == null -> return@runCatching null
                                    startsWith("jar:") -> this
                                    startsWith("file:") -> "jar:$this"
                                    else -> return@runCatching null
                                }
                            }.run { substring(0, lastIndexOf("!") + 1) } + "/META-INF/MANIFEST.MF"

                            info(null, "Resolving $PLUGIN_NAME version with: $manifestPath")
                            URL(manifestPath).openStream().use {
                                Manifest(it).mainAttributes.getValue("Version")
                            }
                        }.getOrNull() ?: "0.0.0"
                })

                onlyIf {
                    !selfUpdateLock.asPath.exists() || !coroutinesJavaAgent.asPath.exists()
                }
            }
    }
}
