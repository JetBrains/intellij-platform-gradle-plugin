// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradleplugin.asPath
import org.jetbrains.intellij.platform.gradleplugin.isSpecified
import org.jetbrains.intellij.platform.gradleplugin.model.getBootClasspath
import org.jetbrains.intellij.platform.gradleplugin.model.productInfo
import org.jetbrains.intellij.platform.gradleplugin.or
import org.jetbrains.intellij.platform.gradleplugin.propertyProviders.IntelliJPlatformArgumentProvider
import org.jetbrains.intellij.platform.gradleplugin.propertyProviders.LaunchSystemArgumentProvider
import org.jetbrains.intellij.platform.gradleplugin.tasks.base.JetBrainsRuntimeAware
import org.jetbrains.intellij.platform.gradleplugin.tasks.base.PlatformVersionAware
import org.jetbrains.intellij.platform.gradleplugin.tasks.base.RunIdeBase
import org.jetbrains.intellij.platform.gradleplugin.tasks.base.SandboxAware
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Runs the IDE instance with the developed plugin installed.
 *
 * `runIde` task extends the [JavaExec] Gradle task – all properties available in the [JavaExec] as well as the following ones can be used to configure the [RunIdeTask] task.
 *
 * @see [RunIdeBase]
 * @see [JavaExec]
 */
@UntrackedTask(because = "Should always run guest IDE")
abstract class RunIdeTask : JavaExec(), JetBrainsRuntimeAware, PlatformVersionAware, SandboxAware {

    @get:Input
    @get:Optional
    abstract val type: Property<IntelliJPlatformType>

    @get:Input
    @get:Optional
    abstract val version: Property<String>

    @get:Input
    @get:Optional
    abstract val localPath: Property<File>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val intelliJPlatform: ConfigurableFileCollection

    @get:InputFiles
    val intellijPlatformDirectory
        get() = when {
            !type.isSpecified && !version.isSpecified -> intelliJPlatform.singleFile.toPath()
            else -> throw GradleException("Foo")
        }

    /**
     * Represents the path to the coroutines Java agent file.
     */
    @get:Internal
    abstract val coroutinesJavaAgentPath: Property<Path>

    init {
        group = PLUGIN_GROUP_NAME
        description = "Runs the IDE instance with the developed plugin installed."

        mainClass.set("com.intellij.idea.Main")
        enableAssertions = true
    }

    /**
     * Executes the task, configures and runs the IDE.
     */
    @TaskAction
    override fun exec() {
        workingDir = intellijPlatformDirectory.resolve("bin").toFile()
        jvmArgumentProviders.add(IntelliJPlatformArgumentProvider(intellijPlatformDirectory, coroutinesJavaAgentPath.get(), this))
        configureSystemProperties()
        configureClasspath()

        super.exec()
    }

    override fun getExecutable(): String = jetbrainsRuntimeExecutable.asPath.absolutePathString()

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

        classpath += objectFactory.fileCollection().from(
            productInfo.getBootClasspath(intellijPlatformDirectory)
        )
    }

    /**
     * Configures the system properties for the IDE based on the IDEA version.
     */
    private fun configureSystemProperties() {
        systemProperties(systemProperties)

        jvmArgumentProviders.add(
            LaunchSystemArgumentProvider(
                intellijPlatformDirectory,
                sandboxDirectory,
                emptyList(),
//                requiredPluginIds.get(),
            )
        )

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
        systemPropertyIfNotDefined("idea.auto.reload.plugins", true, userDefinedSystemProperties)

        if (!systemProperties.containsKey("idea.platform.prefix")) {
            val prefix = intellijPlatformDirectory.productInfo().productCode

            systemProperty("idea.platform.prefix", prefix)
//            info(context, "Using idea.platform.prefix=$prefix")
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
