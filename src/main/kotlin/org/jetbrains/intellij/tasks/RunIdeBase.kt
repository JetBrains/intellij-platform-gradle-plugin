// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.Version
import org.jetbrains.intellij.error
import org.jetbrains.intellij.getIdeJvmArgs
import org.jetbrains.intellij.getIdeaSystemProperties
import org.jetbrains.intellij.ideBuildNumber
import org.jetbrains.intellij.info
import org.jetbrains.intellij.logCategory
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import kotlin.streams.asSequence

abstract class RunIdeBase(runAlways: Boolean) : JavaExec() {
    companion object {
        private val platformPrefixSystemPropertyRegex = Regex("-Didea.platform.prefix=([A-z]+)")
    }

    private val context = logCategory()

    /**
     * The IDEA dependency sources path.
     * Configured automatically with the [org.jetbrains.intellij.tasks.SetupDependenciesTask.idea] dependency.
     *
     * Default value: `setupDependenciesTask.idea.get().classes.path`
     *
     * TODO: Should be @Internal
     */
    @Input
    val ideDir = objectFactory.property<File>()

    /**
     * Custom JBR version to use for running the IDE.
     *
     * All JetBrains Java versions are available at JetBrains Space Packages, and [GitHub](https://github.com/JetBrains/JetBrainsRuntime/releases).
     *
     * Accepted values:
     * - `8u112b752.4`
     * - `8u202b1483.24`
     * - `11_0_2b159`
     */
    @Input
    @Optional
    val jbrVersion = objectFactory.property<String>()

    /**
     * JetBrains Runtime variant to use when running the IDE with the plugin.
     * See [JetBrains Runtime Releases](https://github.com/JetBrains/JetBrainsRuntime/releases).
     *
     * Default value: `null`
     *
     * Acceptable values:
     * - `jcef`
     * - `sdk`
     * - `fd`
     * - `dcevm`
     * - `nomod`
     *
     * Note: For `JBR 17`, `dcevm` is bundled by default. As a consequence, separated `dcevm` and `nomod` variants are no longer available.
     *
     * **Accepted values:**
     * - `8u112b752.4`
     * - `8u202b1483.24`
     * - `11_0_2b159`
     *
     * All JetBrains Java versions are available at JetBrains Space Packages,
     * and [GitHub](https://github.com/JetBrains/JetBrainsRuntime/releases).
     */
    @Input
    @Optional
    val jbrVariant = objectFactory.property<String>()

    /**
     * Path to the `plugins` directory within the sandbox prepared with [org.jetbrains.intellij.tasks.PrepareSandboxTask].
     * Provided to the `idea.plugins.path` system property.
     *
     * Default value: [org.jetbrains.intellij.tasks.PrepareSandboxTask.getDestinationDir]
     *
     * TODO: Should be @Internal
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.NONE)
    val pluginsDir: DirectoryProperty = objectFactory.directoryProperty()

    /**
     * Enables auto-reload of dynamic plugins.
     * Dynamic plugins will be reloaded automatically when their JARs are modified.
     * This allows a much faster development cycle by avoiding a full restart of the development instance after code changes.
     * Enabled by default in 2020.2 and higher.
     */
    @Input
    @Optional
    val autoReloadPlugins = objectFactory.property<Boolean>()

    /**
     * List of plugins required to be present when running the IDE.
     * Provided to the `idea.required.plugins.id` system property.
     */
    @Internal
    val requiredPluginIds = objectFactory.listProperty<String>()

    /**
     * Path to the `config` directory within the sandbox prepared with [org.jetbrains.intellij.tasks.PrepareSandboxTask].
     * Provided to the `idea.config.path` system property.
     *
     * Default value: [org.jetbrains.intellij.tasks.PrepareSandboxTask.configDir]
     */
    @Internal
    val configDir = objectFactory.property<File>()

    /**
     * Path to the `system` directory within the sandbox prepared with [org.jetbrains.intellij.tasks.PrepareSandboxTask].
     * Provided to the `idea.system.path` system property.
     *
     * Default value: [org.jetbrains.intellij.IntelliJPluginExtension.sandboxDir]/system
     */
    @Internal
    val systemDir = objectFactory.property<File>()

    /**
     * The IDEA binary working directory.
     *
     * Default value: [org.jetbrains.intellij.tasks.SetupDependenciesTask.idea]/bin
     */
    @Internal
    val projectWorkingDir = objectFactory.property<File>()

    /**
     * Path to the resolved JBR executable.
     */
    @Internal
    val projectExecutable = objectFactory.property<String>()

    private val buildNumber by lazy { ideBuildNumber(ideDir.get()).split('-').last().let(Version::parse) }
    private val build203 by lazy { Version.parse("203.0") }
    private val build221 by lazy { Version.parse("221.0") }
    private val build222 by lazy { Version.parse("222.0") }

    init {
        mainClass.set("com.intellij.idea.Main")
        enableAssertions = true
        if (runAlways) {
            outputs.upToDateWhen { false }
        }
    }

    /**
     * Executes the task, configures and runs the IDE.
     */
    @TaskAction
    override fun exec() {
        workingDir = projectWorkingDir.get()
        configureSystemProperties()
        configureJvmArgs()
        executable(projectExecutable.get())
        configureClasspath()
        super.exec()
    }

    /**
     * Prepares the classpath for the IDE based on the IDEA version.
     */
    private fun configureClasspath() {
        val ideDirFile = ideDir.get()

        executable.takeUnless { it.isNullOrEmpty() }?.let {
            resolveToolsJar(it).takeIf(File::exists) ?: Jvm.current().toolsJar
        }?.let {
            classpath += objectFactory.fileCollection().from(it)
        }

        classpath += when {
            buildNumber > build221 -> listOf(
                "$ideDirFile/lib/3rd-party-rt.jar",
                "$ideDirFile/lib/util.jar",
                "$ideDirFile/lib/util_rt.jar",
                "$ideDirFile/lib/jna.jar",
            )

            buildNumber > build203 -> listOf(
                "$ideDirFile/lib/bootstrap.jar",
                "$ideDirFile/lib/util.jar",
                "$ideDirFile/lib/jdom.jar",
                "$ideDirFile/lib/log4j.jar",
                "$ideDirFile/lib/jna.jar",
            )

            else -> listOf(
                "$ideDirFile/lib/bootstrap.jar",
                "$ideDirFile/lib/extensions.jar",
                "$ideDirFile/lib/util.jar",
                "$ideDirFile/lib/jdom.jar",
                "$ideDirFile/lib/log4j.jar",
                "$ideDirFile/lib/jna.jar",
                "$ideDirFile/lib/trove4j.jar",
            )
        }.let { objectFactory.fileCollection().from(it) }
    }

    /**
     * Configures the system properties for the IDE based on the IDEA version.
     */
    private fun configureSystemProperties() {
        systemProperties(systemProperties)
        systemProperties(getIdeaSystemProperties(configDir.get(), systemDir.get(), pluginsDir.get().asFile, requiredPluginIds.get()))
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

        if (!userDefinedSystemProperties.containsKey("idea.auto.reload.plugins") && autoReloadPlugins.get()) {
            systemProperty("idea.auto.reload.plugins", "true")
        }

        if (!systemProperties.containsKey("idea.platform.prefix")) {
            val prefix = findIdePrefix()
            if (prefix == null && !ideBuildNumber(ideDir.get()).startsWith("IU-")) {
                throw TaskExecutionException(this,
                    GradleException("Cannot find IDE platform prefix. Please create a bug report at https://github.com/jetbrains/gradle-intellij-plugin. " +
                        "As a workaround specify `idea.platform.prefix` system property for task `${this.name}` manually."))
            }

            if (prefix != null) {
                systemProperty("idea.platform.prefix", prefix)
            }
            info(context, "Using idea.platform.prefix=$prefix")
        }

        if (buildNumber > build221) {
            systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
        }
    }

    /**
     * Resolves the IDE prefix.
     */
    private fun findIdePrefix(): String? {
        info(context, "Looking for platform prefix")
        val prefix = Files.list(ideDir.get().toPath().resolve("bin"))
            .asSequence()
            .filter { file -> file.fileName.toString().endsWith(".sh") || file.fileName.toString().endsWith(".bat") }
            .flatMap { file -> Files.lines(file).asSequence() }
            .mapNotNull { line -> platformPrefixSystemPropertyRegex.find(line)?.groupValues?.getOrNull(1) }
            .firstOrNull()

        return when {
            prefix != null -> {
                prefix
            }

            OperatingSystem.current().isMacOsX -> {
                val infoPlist = ideDir.get().toPath().resolve("Info.plist")
                try {
                    Files.lines(infoPlist).asSequence().windowed(2)
                        .filter { it.first().trim() == "<key>idea.platform.prefix</key>" }
                        .map { it.last().trim().removeSurrounding("<string>", "</string>") }
                        .firstOrNull()
                } catch (e: FileNotFoundException) {
                    null
                } catch (t: Throwable) {
                    error(context, "Cannot find prefix in $infoPlist", t)
                    null
                }
            }

            else -> {
                null
            }
        }
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
     * Configures JVM arguments based on the current IDE version.
     */
    protected open fun configureJvmArgs() {
        jvmArgs = getIdeJvmArgs(this, jvmArgs, ideDir.get()) + listOf(
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
            "--add-opens=java.base/java.text=ALL-UNNAMED",
            "--add-opens=java.base/java.time=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED",
            "--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED",
            "--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED",
//            "--add-opens=java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
            "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
            "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
//            "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED",
//            "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
            "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED",
            "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED",
            "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED",
            "--add-opens=java.base/sun.security.util=ALL-UNNAMED",
        )
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
