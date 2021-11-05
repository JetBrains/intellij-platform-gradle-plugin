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

@Suppress("UnstableApiUsage")
abstract class RunIdeBase(runAlways: Boolean) : JavaExec() {
    companion object {
        private val platformPrefixSystemPropertyRegex = Regex("-Didea.platform.prefix=([A-z]+)")
    }
    
    private val context = logCategory()

    @Input
    val ideDir = objectFactory.property<File>()

    @Input
    @Optional
    val jbrVersion = objectFactory.property<String>()

    @InputDirectory
    @PathSensitive(PathSensitivity.NONE)
    val pluginsDir: DirectoryProperty = objectFactory.directoryProperty()

    /**
     * Enables auto-reload of dynamic plugins. Dynamic plugins will be reloaded automatically when their JARs are
     * modified. This allows a much faster development cycle by avoiding a full restart of the development instance
     * after code changes. Enabled by default in 2020.2 and higher.
     */
    @Input
    @Optional
    val autoReloadPlugins = objectFactory.property<Boolean>()

    @Internal
    val requiredPluginIds = objectFactory.listProperty<String>()

    @Internal
    val configDir = objectFactory.property<File>()

    @Internal
    val systemDir = objectFactory.property<File>()

    @Internal
    val projectWorkingDir = objectFactory.property<File>()

    @Internal
    val projectExecutable = objectFactory.property<String>()

    init {
        mainClass.set("com.intellij.idea.Main")
        enableAssertions = true
        if (runAlways) {
            outputs.upToDateWhen { false }
        }
    }

    @TaskAction
    override fun exec() {
        workingDir = projectWorkingDir.get()
        configureSystemProperties()
        configureJvmArgs()
        executable(projectExecutable.get())
        configureClasspath()
        super.exec()
    }

    private fun configureClasspath() {
        val ideDirFile = ideDir.get()

        executable.takeUnless { it.isNullOrEmpty() }?.let {
            resolveToolsJar(it).takeIf(File::exists) ?: Jvm.current().toolsJar
        }?.let {
            classpath += objectFactory.fileCollection().from(it)
        }

        val buildNumber = ideBuildNumber(ideDir.get()).split('-').last()
        if (Version.parse(buildNumber) > Version.parse("203.0")) {
            classpath += objectFactory.fileCollection().from(
                    "$ideDirFile/lib/bootstrap.jar",
                    "$ideDirFile/lib/util.jar",
                    "$ideDirFile/lib/jdom.jar",
                    "$ideDirFile/lib/log4j.jar",
                    "$ideDirFile/lib/jna.jar",
            )
        } else {
            classpath += objectFactory.fileCollection().from(
                    "$ideDirFile/lib/bootstrap.jar",
                    "$ideDirFile/lib/extensions.jar",
                    "$ideDirFile/lib/util.jar",
                    "$ideDirFile/lib/jdom.jar",
                    "$ideDirFile/lib/log4j.jar",
                    "$ideDirFile/lib/jna.jar",
                    "$ideDirFile/lib/trove4j.jar",
            )
        }
    }

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

        if (!userDefinedSystemProperties.containsKey("idea.auto.reload.plugins") && autoReloadPlugins.get()) {
            systemProperty("idea.auto.reload.plugins", "true")
        }

        if (!systemProperties.containsKey("idea.platform.prefix")) {
            val prefix = findIdePrefix()
            if (prefix == null && !ideBuildNumber(ideDir.get()).startsWith("IU-")) {
                throw TaskExecutionException(this, GradleException("Cannot find IDE platform prefix. Please create a bug report at https://github.com/jetbrains/gradle-intellij-plugin. " +
                        "As a workaround specify `idea.platform.prefix` system property for task `${this.name}` manually."))
            }

            if (prefix != null) {
                systemProperty("idea.platform.prefix", prefix)
            }
            info(context, "Using idea.platform.prefix=$prefix")
        }
    }

    private fun findIdePrefix(): String? {
        info(context, "Looking for platform prefix")
        val prefix = Files.list(ideDir.get().toPath().resolve("bin"))
            .asSequence()
            .filter { file -> file.fileName.toString().endsWith(".sh") || file.fileName.toString().endsWith(".bat")}
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

    private fun systemPropertyIfNotDefined(name: String, value: Any, userDefinedSystemProperties: Map<String, Any>) {
        if (!userDefinedSystemProperties.containsKey(name)) {
            systemProperty(name, value)
        }
    }

    private fun configureJvmArgs() {
        jvmArgs = getIdeJvmArgs(this, jvmArgs, ideDir.get())
    }

    private fun resolveToolsJar(javaExec: String): File {
        val binDir = File(javaExec).parent
        val path = when {
            OperatingSystem.current().isMacOsX -> "../../lib/tools.jar"
            else -> "../lib/tools.jar"
        }
        return File(binDir, path)
    }
}
