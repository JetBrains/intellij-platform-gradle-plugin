// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.StartParameter
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.of
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.providers.CurrentPluginVersionValueSource
import org.jetbrains.intellij.platform.gradle.providers.LatestPluginVersionValueSource
import org.jetbrains.intellij.platform.gradle.tasks.aware.CoroutinesJavaAgentAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.utils.*
import java.net.URLClassLoader
import java.time.LocalDate
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.*

private const val CLEAN = "clean"

/**
 * Executes before every other task introduced by IntelliJ Platform Gradle Plugin to prepare it to run.
 * It is responsible for:
 *
 * - checking if the project uses IntelliJ Platform Gradle Plugin in the latest available version
 * - preparing the KotlinX Coroutines Java Agent file to enable coroutines debugging when developing the plugin
 *
 * The self-update check can be disabled via [GradleProperties.SelfUpdateCheck] Gradle property.
 * To make the Coroutines Java Agent available for the task, inherit from [CoroutinesJavaAgentAware].
 */
@UntrackedTask(because = "Should always run")
abstract class InitializeIntelliJPlatformPluginTask : DefaultTask(), IntelliJPlatformVersionAware {

    /**
     * Holds the [Configurations.INTELLIJ_PLATFORM_CLASSPATH] configuration with the IntelliJ Platform classpath.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val intelliJPlatformClasspathConfiguration: ConfigurableFileCollection

    /**
     * Determines if the operation is running in offline mode and depends on Gradle start parameters.
     *
     * Default value: [StartParameter.isOffline]
     */
    @get:Internal
    abstract val offline: Property<Boolean>

    /**
     * Represents the property for checking if self-update checks are enabled.
     *
     * Default value: [GradleProperties.SelfUpdateCheck]
     */
    @get:Internal
    abstract val selfUpdateCheck: Property<Boolean>

    /**
     * Represents a lock file used to limit the plugin version checks in time.
     * If the file is missing and other conditions are met, the version check is performed.
     */
    @get:Internal
    abstract val selfUpdateLock: RegularFileProperty

    /**
     * Specifies the Java Agent file for the Coroutines library required to enable coroutines debugging.
     *
     * Default value: [ProjectLayout.getBuildDirectory]/tmp/initializeIntelliJPlatformPlugin/coroutines-javaagent.jar
     *
     * @see [CoroutinesJavaAgentAware]
     */
    @get:OutputFile
    abstract val coroutinesJavaAgent: RegularFileProperty

    /**
     * Represents the main class name used as an entry point before the standard application initialization.
     */
    @get:Internal
    abstract val coroutinesJavaAgentPremainClass: Property<String>

    /**
     * Represents a lock file used to store the IntelliJ Platform build number.
     * If the build number is the same, the creation of the coroutines Java agent file is skipped.
     */
    @get:Internal
    abstract val coroutinesJavaAgentLock: RegularFileProperty

    /**
     * Represents the current version of the plugin.
     */
    @get:Internal
    abstract val pluginVersion: Property<String>

    /**
     * Represents the latest version of the plugin.
     */
    @get:Internal
    abstract val latestPluginVersion: Property<String>

    /**
     * Defines that the current project has only the [Plugins.MODULE] applied but no [Plugin.ID].
     */
    @get:Internal
    abstract val module: Property<Boolean>

    private val log = Logger(javaClass)

    @TaskAction
    fun initialize() {
        checkPluginVersion()
        createCoroutinesJavaAgentFile()
    }

    /**
     * Checks if the plugin is up-to-date.
     */
    private fun checkPluginVersion() {
        if (module.get() || !selfUpdateCheck.get() || offline.get()) {
            return
        }

        val lastUpdate = runCatching {
            LocalDate.parse(selfUpdateLock.asPath.readText().trim())
        }.getOrNull()

        if (lastUpdate == LocalDate.now()) {
            return
        }

        try {
            val version = Version.parse(pluginVersion.get())
            val latestVersion = Version.parse(latestPluginVersion.get())

            if (version < latestVersion) {
                log.warn("${Plugin.NAME} is outdated: $version. Update `${Plugin.ID}` to: $latestVersion")
            }

            with(selfUpdateLock.asPath) {
                writeText(LocalDate.now().toString())
            }
        } catch (e: Exception) {
            log.error(e.message.orEmpty(), e)
        }
    }

    /**
     * Creates the Java Agent file for the Coroutines library required to enable coroutines debugging.
     * If the current IntelliJ Platform build number matches the one stored in the lock file,
     * the creation of the coroutines Java agent file is skipped.
     */
    private fun createCoroutinesJavaAgentFile() {
        if (module.get()) {
            return
        }

        val storedBuildNumber by lazy {
            runCatching {
                coroutinesJavaAgentLock.asPath.readText().trim()
            }.getOrNull()
        }

        if (coroutinesJavaAgent.asPath.exists() && productInfo.buildNumber == storedBuildNumber) {
            return
        }

        val manifest = Manifest(
            """
            Manifest-Version: 1.0
            Premain-Class: ${coroutinesJavaAgentPremainClass.get()}
            Can-Retransform-Classes: true
            Multi-Release: true
            
            """.trimIndent().byteInputStream()
        )

        JarOutputStream(coroutinesJavaAgent.asPath.outputStream(), manifest).close()
        coroutinesJavaAgentLock.asPath.writeText(productInfo.buildNumber)
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Initializes the IntelliJ Platform Gradle Plugin"
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
                val cachePathProvider = project.extensionProvider.map { it.cachePath }

                intelliJPlatformClasspathConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_CLASSPATH]

                offline.convention(project.gradle.startParameter.isOffline)
                selfUpdateCheck.convention(project.providers[GradleProperties.SelfUpdateCheck])
                selfUpdateLock.convention(
                    project.layout.file(cachePathProvider.map {
                        it.createDirectories().resolve("self-update.lock").toFile()
                    })
                )
                coroutinesJavaAgentLock.convention(project.layout.buildDirectory.file("coroutines-javaagent.lock"))
                coroutinesJavaAgent.convention(project.layout.buildDirectory.file("coroutines-javaagent.jar"))
                pluginVersion.convention(project.providers.of(CurrentPluginVersionValueSource::class) {})
                latestPluginVersion.convention(project.providers.of(LatestPluginVersionValueSource::class) {})
                module.convention(project.provider { project.pluginManager.isModule })
                coroutinesJavaAgentPremainClass.convention(project.provider {
                    val urls = intelliJPlatformClasspathConfiguration.files.map { it.toURI().toURL() }.toTypedArray()
                    URLClassLoader(urls).use {
                        listOf(
                            "kotlinx.coroutines.debug.AgentPremain",
                            "kotlinx.coroutines.debug.internal.AgentPremain"
                        ).firstNotNullOfOrNull { fqn -> it.runCatching { loadClass(fqn) }.getOrNull() }
                    }?.name
                })

                mustRunAfter(CLEAN)
            }
    }
}
