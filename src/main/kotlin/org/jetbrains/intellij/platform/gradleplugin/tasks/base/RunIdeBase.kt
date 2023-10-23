// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks.base

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradleplugin.*
import org.jetbrains.intellij.platform.gradleplugin.model.productInfo
import java.io.File
import java.nio.file.Path

/**
 * Base task for running an IDE with the current plugin in various modes.
 */
@UntrackedTask(because = "Runs an IDE which should happen every time this task is run.")
abstract class RunIdeBase : JavaExec() {

    private val context = logCategory()

    /**
     * The IDEA dependency sources path.
     * Configured automatically with the [SetupDependenciesTask.idea] dependency.
     *
     * Default value: `setupDependenciesTask.idea.get().classes.path`
     */
    @get:Input
    abstract val ideDir: Property<File>


//    /**
//     * Enables auto-reload of dynamic plugins.
//     * Dynamic plugins will be reloaded automatically when their JARs are modified.
//     * This allows a much faster development cycle by avoiding a full restart of the development instance after code changes.
//     * Enabled by default in 2020.2 and higher.
//     */
//    @get:Input
//    @get:Optional
//    abstract val autoReloadPlugins: Property<Boolean>

    /**
     * List of plugins required to be present when running the IDE.
     * Provided to the `idea.required.plugins.id` system property.
     */
    @get:Internal
    abstract val requiredPluginIds: ListProperty<String>


    /**
     * The IDEA binary working directory.
     *
     * Default value: [SetupDependenciesTask.idea]/bin
     */
    @get:Internal
    abstract val projectWorkingDir: Property<File>

    /**
     * Path to the resolved JBR executable.
     */
    @get:Internal
    abstract val projectExecutable: Property<String>

    /**
     * Represents the path to the coroutines Java agent file.
     */
    @get:Internal
    abstract val coroutinesJavaAgentPath: Property<Path>

    private val ideDirPath by lazy {
        ideDir.get().toPath()
    }

    init {
        mainClass.set("com.intellij.idea.Main")
        enableAssertions = true
    }

    /**
     * Executes the task, configures and runs the IDE.
     */
    @TaskAction
    override fun exec() {
        throw Exception("OBSOLETE")
        workingDir = projectWorkingDir.get()
//        jvmArgumentProviders.add(IntelliJPlatformArgumentProvider(ideDir.get().toPath(), coroutinesJavaAgentPath.get(), this))
        configureSystemProperties()
        configureClasspath()

        super.exec()
    }

    override fun getExecutable(): String = projectExecutable.get()

    /**
     * Prepares the classpath for the IDE based on the IDEA version.
     */
    private fun configureClasspath() {
        executable
            .takeUnless(String?::isNullOrEmpty)
            ?.let {
                resolveToolsJar(it)
                    .takeIf(File::exists)
                    .or(Jvm.current().toolsJar)
            }
            ?.let {
                classpath += objectFactory.fileCollection().from(it)
            }

//        classpath += getIdeaClasspath(ideDirPath)
//            .let { objectFactory.fileCollection().from(it) }
    }

    /**
     * Configures the system properties for the IDE based on the IDEA version.
     */
    private fun configureSystemProperties() {
        systemProperties(systemProperties)

//        jvmArgumentProviders.add(
//            LaunchSystemArgumentProvider(
//                ideDirPath,
//                configDir.get(),
//                systemDir.get(),
//                pluginsDir.asFile,
//                requiredPluginIds.get(),
//            )
//        )

        val operatingSystem = OperatingSystem.current()
        val userDefinedSystemProperties = systemProperties

        if (operatingSystem.isMacOsX) {
            systemPropertyIfNotDefined("idea.smooth.progress", false, userDefinedSystemProperties)
            systemPropertyIfNotDefined("apple.laf.useScreenMenuBar", true, userDefinedSystemProperties)
            systemPropertyIfNotDefined("apple.awt.fileDialogForDirectories", true, userDefinedSystemProperties)
        } else if (operatingSystem.isUnix) {
            systemPropertyIfNotDefined("sun.awt.disablegrab", true, userDefinedSystemProperties)
        }

        systemPropertyIfNotDefined("idea.classpath.index.enabled", false, userDefinedSystemProperties)
        systemPropertyIfNotDefined("idea.is.internal", true, userDefinedSystemProperties)
        systemPropertyIfNotDefined("jdk.module.illegalAccess.silent", true, userDefinedSystemProperties)

//        if (!userDefinedSystemProperties.containsKey("idea.auto.reload.plugins") && autoReloadPlugins.get()) {
//            systemProperty("idea.auto.reload.plugins", "true")
//        }

        if (!systemProperties.containsKey("idea.platform.prefix")) {
            val prefix = ideDir.get().toPath().productInfo().productCode

            systemProperty("idea.platform.prefix", prefix)
            info(context, "Using idea.platform.prefix=$prefix")
        }

        systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
        systemPropertyIfNotDefined("idea.vendor.name", "JetBrains", userDefinedSystemProperties)
        systemPropertyIfNotDefined("idea.plugin.in.sandbox.mode", true, userDefinedSystemProperties)
    }

    /**
     * Helper function to set system property if it is not defined yet.
     */
    private fun systemPropertyIfNotDefined(name: String, value: Any, userDefinedSystemProperties: Map<String, Any>) {
        if (!userDefinedSystemProperties.containsKey(name)) {
            systemProperty(name, value)
        }
    }

    /**
     * Resolves the path to the `tools.jar` library.
     */
    private fun resolveToolsJar(javaExec: String): File {
        val binDir = File(javaExec).parent
        val path = when {
            OperatingSystem.current().isMacOsX -> "../../lib/tools.jar"
            else -> "../lib/tools.jar"
        }
        return File(binDir, path)
    }
}
