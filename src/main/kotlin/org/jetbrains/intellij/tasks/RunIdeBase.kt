package org.jetbrains.intellij.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.VersionNumber
import org.jetbrains.intellij.VERSION_PATTERN
import org.jetbrains.intellij.getIdeJvmArgs
import org.jetbrains.intellij.getIdeaSystemProperties
import org.jetbrains.intellij.ideBuildNumber
import org.jetbrains.intellij.resolveToolsJar
import java.io.File

@Suppress("UnstableApiUsage")
abstract class RunIdeBase(runAlways: Boolean) : JavaExec() {

    private val prefixes = mapOf(
        "IU" to null,
        "IC" to "Idea",
        "RM" to "Ruby",
        "PY" to "Python",
        "PC" to "PyCharmCore",
        "PE" to "PyCharmEdu",
        "PS" to "PhpStorm",
        "WS" to "WebStorm",
        "OC" to "AppCode",
        "CL" to "CLion",
        "DB" to "DataGrip",
        "AI" to "AndroidStudio",
        "GO" to "GoLand",
        "RD" to "Rider",
        "RDCPPP" to "Rider",
    )

    @Internal
    val requiredPluginIds: ListProperty<String> = project.objects.listProperty(String::class.java)

    @InputDirectory
    @PathSensitive(PathSensitivity.NONE)
    val ideDirectory: DirectoryProperty = project.objects.directoryProperty()

    @Input
    @Optional
    val jbrVersion: Property<String> = project.objects.property(String::class.java)

    @Internal
    val configDirectory: Property<File> = project.objects.property(File::class.java)

    @Internal
    val systemDirectory: Property<File> = project.objects.property(File::class.java)

    @InputDirectory
    @PathSensitive(PathSensitivity.NONE)
    val pluginsDirectory: DirectoryProperty = project.objects.directoryProperty()

    /**
     * Enables auto-reload of dynamic plugins. Dynamic plugins will be reloaded automatically when their JARs are
     * modified. This allows a much faster development cycle by avoiding a full restart of the development instance
     * after code changes. Enabled by default in 2020.2 and higher.
     */
    @Input
    @Optional
    val autoReloadPlugins: Property<Boolean> = project.objects.property(Boolean::class.java)

    init {
        main = "com.intellij.idea.Main"
        enableAssertions = true
        if (runAlways) {
            outputs.upToDateWhen { false }
        }
    }

    @Override
    override fun exec() {
        workingDir = project.file("${ideDirectory.get().asFile}/bin/")
        configureClasspath()
        configureSystemProperties()
        configureJvmArgs()
        executable(executable)
        super.exec()
    }

    private fun configureClasspath() {
        val ideDirectoryFile = ideDirectory.get().asFile

        executable.takeUnless { it.isNullOrEmpty() }?.let {
            project.file(resolveToolsJar(it)).takeIf(File::exists) ?: Jvm.current().toolsJar
        }?.let {
            classpath += project.files(it)
        }

        val buildNumber = ideBuildNumber(ideDirectory.get().asFile).split('-').last()
        val version = VersionNumber.parse(buildNumber)
        if (version > VersionNumber.parse("203.0")) {
            classpath += project.files(
                    "$ideDirectoryFile/lib/bootstrap.jar",
                    "$ideDirectoryFile/lib/util.jar",
                    "$ideDirectoryFile/lib/jdom.jar",
                    "$ideDirectoryFile/lib/log4j.jar",
                    "$ideDirectoryFile/lib/jna.jar",
            )
        } else {
            classpath += project.files(
                    "$ideDirectoryFile/lib/bootstrap.jar",
                    "$ideDirectoryFile/lib/extensions.jar",
                    "$ideDirectoryFile/lib/util.jar",
                    "$ideDirectoryFile/lib/jdom.jar",
                    "$ideDirectoryFile/lib/log4j.jar",
                    "$ideDirectoryFile/lib/jna.jar",
                    "$ideDirectoryFile/lib/trove4j.jar",
            )
        }
    }

    private fun configureSystemProperties() {
        systemProperties(systemProperties)
        systemProperties(getIdeaSystemProperties(configDirectory.get(), systemDirectory.get(), pluginsDirectory.get().asFile, requiredPluginIds.get()))
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
            val matcher = VERSION_PATTERN.matcher(ideBuildNumber(ideDirectory.get().asFile))
            if (matcher.find()) {
                val abbreviation = matcher.group(1)
                val prefix = prefixes[abbreviation]
                if (!prefix.isNullOrEmpty()) {
                    systemProperty("idea.platform.prefix", prefix)
                }
            }
        }
    }

    private fun systemPropertyIfNotDefined( name: String,  value: Any, userDefinedSystemProperties: Map<String, Any>) {
        if (!userDefinedSystemProperties.containsKey(name)) {
            systemProperty(name, value)
        }
    }

    private fun configureJvmArgs() {
        jvmArgs = getIdeJvmArgs(this, jvmArgs ?: emptyList(), ideDirectory.get().asFile)
    }
}
