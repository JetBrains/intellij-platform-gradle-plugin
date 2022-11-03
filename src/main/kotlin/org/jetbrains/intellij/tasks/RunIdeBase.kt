// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.dd.plist.NSDictionary
import com.dd.plist.PropertyListParser
import com.jetbrains.plugin.structure.base.utils.hasExtension
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.*
import org.jetbrains.intellij.IntelliJPluginConstants.GITHUB_REPOSITORY
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_INTELLIJ_ULTIMATE
import java.io.File
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
     */
    @get:Input
    abstract val ideDir: Property<File>

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
    @get:Input
    @get:Optional
    abstract val jbrVersion: Property<String>

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
    @get:Input
    @get:Optional
    abstract val jbrVariant: Property<String>

    /**
     * Path to the `plugins` directory within the sandbox prepared with [org.jetbrains.intellij.tasks.PrepareSandboxTask].
     * Provided to the `idea.plugins.path` system property.
     *
     * Default value: [org.jetbrains.intellij.tasks.PrepareSandboxTask.getDestinationDir]
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pluginsDir: DirectoryProperty

    /**
     * Enables auto-reload of dynamic plugins.
     * Dynamic plugins will be reloaded automatically when their JARs are modified.
     * This allows a much faster development cycle by avoiding a full restart of the development instance after code changes.
     * Enabled by default in 2020.2 and higher.
     */
    @get:Input
    @get:Optional
    abstract val autoReloadPlugins: Property<Boolean>

    /**
     * List of plugins required to be present when running the IDE.
     * Provided to the `idea.required.plugins.id` system property.
     */
    @get:Internal
    abstract val requiredPluginIds: ListProperty<String>

    /**
     * Path to the `config` directory within the sandbox prepared with [org.jetbrains.intellij.tasks.PrepareSandboxTask].
     * Provided to the `idea.config.path` system property.
     *
     * Default value: [org.jetbrains.intellij.tasks.PrepareSandboxTask.configDir]
     */
    @get:Internal
    abstract val configDir: Property<File>

    /**
     * Path to the `system` directory within the sandbox prepared with [org.jetbrains.intellij.tasks.PrepareSandboxTask].
     * Provided to the `idea.system.path` system property.
     *
     * Default value: [org.jetbrains.intellij.IntelliJPluginExtension.sandboxDir]/system
     */
    @get:Internal
    abstract val systemDir: Property<File>

    /**
     * The IDEA binary working directory.
     *
     * Default value: [org.jetbrains.intellij.tasks.SetupDependenciesTask.idea]/bin
     */
    @get:Internal
    abstract val projectWorkingDir: Property<File>

    /**
     * Path to the resolved JBR executable.
     */
    @get:Internal
    abstract val projectExecutable: Property<String>

    private val ideDirFile by lazy { ideDir.get() }
    private val infoPlist by lazy {
        ideDirFile.resolve("Info.plist").takeIf(File::exists)?.let {
            PropertyListParser.parse(it) as NSDictionary
        }
    }

    private val buildNumber by lazy { ideDirFile.let(::ideBuildNumber).split('-').last().let(Version::parse) }
    private val build221 by lazy { Version.parse("221.0") }

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
        executable
            .takeUnless { it.isNullOrEmpty() }
            ?.let {
                resolveToolsJar(it).takeIf(File::exists) ?: Jvm.current().toolsJar
            }
            ?.let {
                classpath += objectFactory.fileCollection().from(it)
            }

        classpath += getIdeaClasspath(ideDirFile).let {
            objectFactory.fileCollection().from(it)
        }

        println("classpath.joinToString() = ${classpath.joinToString()}")
    }

    /**
     * Configures the system properties for the IDE based on the IDEA version.
     */
    private fun configureSystemProperties() {
        systemProperties(systemProperties)
        systemProperties(
            getIdeaSystemProperties(
                ideDirFile,
                configDir.get(),
                systemDir.get(),
                pluginsDir.get().asFile,
                requiredPluginIds.get()
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

        if (!userDefinedSystemProperties.containsKey("idea.auto.reload.plugins") && autoReloadPlugins.get()) {
            systemProperty("idea.auto.reload.plugins", "true")
        }

        if (!systemProperties.containsKey("idea.platform.prefix")) {
            val prefix = findIdePrefix()
            if (prefix == null && !ideBuildNumber(ideDir.get()).startsWith("$PLATFORM_TYPE_INTELLIJ_ULTIMATE-")) {
                throw TaskExecutionException(
                    this,
                    GradleException(
                        "Cannot find IDE platform prefix. Please create a bug report at $GITHUB_REPOSITORY. " +
                                "As a workaround specify `idea.platform.prefix` system property for task `${this.name}` manually."
                    )
                )
            }

            if (prefix != null) {
                systemProperty("idea.platform.prefix", prefix)
            }
            info(context, "Using idea.platform.prefix=$prefix")
        }

        if (buildNumber >= build221) {
            systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
        }
        systemPropertyIfNotDefined("idea.vendor.name", "JetBrains", userDefinedSystemProperties)
        systemPropertyIfNotDefined("idea.plugin.in.sandbox.mode", true, userDefinedSystemProperties)
    }

    /**
     * Resolves the IDE prefix.
     */
    private fun findIdePrefix(): String? {
        info(context, "Looking for platform prefix")

        val prefix = Files.list(ideDirFile.toPath().resolve("bin"))
            .asSequence()
            .filter { file -> file.hasExtension("sh") || file.hasExtension("bat") }
            .flatMap { file -> Files.lines(file).asSequence() }
            .mapNotNull { line -> platformPrefixSystemPropertyRegex.find(line)?.groupValues?.getOrNull(1) }
            .firstOrNull()

        return when {
            prefix != null -> prefix

            OperatingSystem.current().isMacOsX -> infoPlist
                ?.getDictionary("JVMOptions")
                ?.getDictionary("Properties")
                ?.getValue("idea.platform.prefix")
                .ifNull {
                    error(context, "Cannot find prefix in $infoPlist")
                }

            else -> null
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
    private fun configureJvmArgs() {
        jvmArgs = collectJvmArgs()
    }

    protected open fun collectJvmArgs() = getIdeaJvmArgs(this, jvmArgs, ideDir.get())

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
