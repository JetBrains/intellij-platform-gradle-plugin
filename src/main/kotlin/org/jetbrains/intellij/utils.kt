@file:JvmName("Utils")

package org.jetbrains.intellij

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.base.utils.isJar
import com.jetbrains.plugin.structure.base.utils.isZip
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import groovy.lang.Closure
import groovy.util.Node
import groovy.util.XmlParser
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.AbstractFileFilter
import org.apache.commons.io.filefilter.FalseFileFilter
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.JavaForkOptions
import org.jetbrains.intellij.model.IdeaPlugin
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Properties
import java.util.function.BiConsumer
import java.util.function.Predicate

val VERSION_PATTERN = "^([A-Z]+)-([0-9.A-z]+)\\s*$".toPattern()
val MAJOR_VERSION_PATTERN = "(RIDER-)?\\d{4}\\.\\d-SNAPSHOT".toPattern()

fun mainSourceSet(project: Project): SourceSet {
    val javaConvention = project.convention.getPlugin(JavaPluginConvention::class.java)
    return javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
}

fun sourcePluginXmlFiles(project: Project): FileCollection {
    val result = HashSet<File>()
    mainSourceSet(project).resources.srcDirs.forEach {
        val pluginXml = File(it, "META-INF/plugin.xml")
        if (pluginXml.exists()) {
            try {
                if (parseXml(pluginXml).name() == "idea-plugin") {
                    result += pluginXml
                }
            } catch (e: SAXParseException) {
                warn(project, "Cannot read ${pluginXml}. Skipping", e)
            }
        }
    }
    return project.files(result)
}

fun getIdeaSystemProperties(
    configDirectory: File,
    systemDirectory: File,
    pluginsDirectory: File,
    requirePluginIds: List<String>,
): Map<String, String> {
    val result = mapOf(
        "idea.config.path" to configDirectory.absolutePath,
        "idea.system.path" to systemDirectory.absolutePath,
        "idea.plugins.path" to pluginsDirectory.absolutePath,
    )
    if (requirePluginIds.isNotEmpty()) {
        return result + mapOf("idea.required.plugins.id" to requirePluginIds.joinToString(","))
    }
    return result
}

fun getIdeJvmArgs(options: JavaForkOptions, arguments: List<String>, ideDirectory: File): List<String> {
    options.maxHeapSize = options.maxHeapSize ?: "512m"
    options.minHeapSize = options.minHeapSize ?: "256m"

    val bootJar = File(ideDirectory, "lib/boot.jar")
    if (bootJar.exists()) {
        return arguments + "-Xbootclasspath/a:${bootJar.absolutePath}"
    }
    return arguments
}

fun ideSdkDirectory(project: Project, alternativeIdePath: String?, ideDirectory: File): File {
    if (alternativeIdePath.isNullOrEmpty()) {
        return ideDirectory
    }

    val dir = ideaDir(alternativeIdePath)
    if (dir.exists()) {
        return dir
    }

    error(project, "Cannot find alternate SDK path: $dir. Default IDE will be used : $ideDirectory")
    return ideDirectory
}

fun ideBuildNumber(ideDirectory: File): String {
    if (!OperatingSystem.current().isMacOsX) {
        return File(ideDirectory, "build.txt").readText().trim()
    }
    File(ideDirectory, "Resources/build.txt").run {
        if (exists()) {
            return readText().trim()
        }
    }
    return File(ideDirectory, "build.txt").readText().trim()
}

fun ideaDir(path: String) = File(path).let {
    it.takeUnless { it.name.endsWith(".app") } ?: File(it, "Contents")
}

fun getPluginIds(project: Project) = sourcePluginXmlFiles(project).files.map {
    parsePluginXml(it, IdeaPlugin::class.java).id
}

fun <T> parsePluginXml(stream: InputStream, valueType: Class<T>): T = XmlMapper()
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .readValue(stream, valueType)

// TODO: rename to parseXml
fun <T> parsePluginXml(file: File, valueType: Class<T>) = parsePluginXml(file.inputStream(), valueType)

// TODO: migrate to parsePluginXml
fun parseXml(file: File) = parseXml(FileInputStream(file))

// TODO: migrate to parsePluginXml
fun parseXml(inputStream: InputStream): Node {
    val parser = XmlParser(false, true, true)
    parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    parser.errorHandler = object : ErrorHandler {
        override fun warning(exception: SAXParseException) {
        }

        override fun error(exception: SAXParseException) {
            throw exception
        }

        override fun fatalError(exception: SAXParseException) {
            throw exception
        }
    }
    val input = InputSource(InputStreamReader(inputStream, "UTF-8"))
    input.encoding = "UTF-8"
    inputStream.use {
        return parser.parse(input)
    }
}

fun isJarFile(file: File) = file.toPath().isJar()

fun isZipFile(file: File) = file.toPath().isZip()

fun stringInput(input: Any?) = when (input) {
    null -> ""
    is Closure<*> -> input.call().toString()
    else -> input.toString()
}

fun stringListInput(input: List<Any>) = input.map { stringInput(it) }

fun collectJars(directory: File, filter: Predicate<File>): Collection<File> = when {
    !directory.isDirectory -> emptyList()
    else -> FileUtils.listFiles(directory, object : AbstractFileFilter() {
        override fun accept(file: File) = isJarFile(file) && filter.test(file)
    }, FalseFileFilter.FALSE)
}

fun resolveToolsJar(javaExec: String): String {
    val binDir = File(javaExec).parent
    return when {
        OperatingSystem.current().isMacOsX -> "$binDir/../../lib/tools.jar"
        else -> "$binDir/../lib/tools.jar"
    }
}

fun getBuiltinJbrVersion(ideDirectory: File): String? {
    val dependenciesFile = File(ideDirectory, "dependencies.txt")
    if (dependenciesFile.exists()) {
        val properties = Properties()
        val reader = FileReader(dependenciesFile)
        try {
            properties.load(reader)
            return properties.getProperty("jdkBuild")
        } catch (ignore: IOException) {
        } finally {
            reader.close()
        }
    }
    return null
}

fun unzip(
    zipFile: File,
    directory: File,
    project: Project,
    isUpToDate: Predicate<File>? = null,
    markUpToDate: BiConsumer<File, File>? = null,
    targetDirName: String? = null,
): File {
    val targetDirectory = File(directory, targetDirName ?: zipFile.name.removeSuffix(".zip"))
    val markerFile = File(targetDirectory, "markerFile")
    if (markerFile.exists() && (isUpToDate == null || isUpToDate.test(markerFile))) {
        return targetDirectory
    }

    if (targetDirectory.exists()) {
        targetDirectory.deleteRecursively()
    }
    targetDirectory.mkdir()

    debug(project, "Unzipping ${zipFile.name}")
    project.copy {
        it.from(project.zipTree(zipFile))
        it.into(targetDirectory)
    }
    debug(project, "Unzipped ${zipFile.name}")

    markerFile.createNewFile()
    markUpToDate?.accept(targetDirectory, markerFile)
    return targetDirectory
}

fun releaseType(version: String): String {
    if (version.endsWith("-EAP-SNAPSHOT") ||
        version.endsWith("-EAP-CANDIDATE-SNAPSHOT") ||
        version.endsWith("-CUSTOM-SNAPSHOT") ||
        MAJOR_VERSION_PATTERN.matcher(version).matches()
    ) {
        return "snapshots"
    }
    if (version.endsWith("-SNAPSHOT")) {
        return "nightly"
    }
    return "releases"
}

// TODO simplify that - hack for Groovy
fun error(context: Any, message: String) = log(LogLevel.ERROR, context, message, null)
fun error(context: Any, message: String, e: Throwable) = log(LogLevel.ERROR, context, message, e)

fun warn(context: Any, message: String) = log(LogLevel.WARN, context, message, null)
fun warn(context: Any, message: String, e: Throwable) = log(LogLevel.WARN, context, message, e)

fun info(context: Any, message: String) = log(LogLevel.INFO, context, message, null)
fun info(context: Any, message: String, e: Throwable) = log(LogLevel.INFO, context, message, e)

fun debug(context: Any, message: String) = log(LogLevel.DEBUG, context, message, null)
fun debug(context: Any, message: String, e: Throwable) = log(LogLevel.DEBUG, context, message, e)

fun log(level: LogLevel, context: Any, message: String, e: Throwable?) {
    val category = when (context) {
        is Project -> "gradle-intellij-plugin :${context.name}"
        is Task -> "gradle-intellij-plugin :${context.project.name}:${context.name}"
        else -> "gradle-intellij-plugin"
    }

    // TODO: Use IntelliJPlugin.LOG
    val LOG = Logging.getLogger("IntelliJPlugin")
    if (e != null && level != LogLevel.ERROR && !LOG.isDebugEnabled) {
        LOG.log(level, "[$category] $message. Run with --debug option to get more log output.")
    } else {
        LOG.log(level, "[$category] $message", e)
    }
}

fun createPlugin(artifact: File, validatePluginXml: Boolean, loggingContext: Any): IdePlugin? {
    val creationResult = IdePluginManager.createManager().createPlugin(artifact.toPath(), validatePluginXml, IdePluginManager.PLUGIN_XML)
    if (creationResult is PluginCreationSuccess) {
        return creationResult.plugin
    } else if (creationResult is PluginCreationFail) {
        val problems = creationResult.errorsAndWarnings.filter { it.level == PluginProblem.Level.ERROR }.joinToString()
        warn(loggingContext, "Cannot create plugin from file ($artifact): $problems")
    } else {
        warn(loggingContext, "Cannot create plugin from file ($artifact). $creationResult")
    }
    return null
}

fun untar(project: Project, from: File, to: File) {
    val tempDir = File(to.parent, to.name + "-temp")
    debug(project, "Unpacking ${from.absolutePath} to ${tempDir.absolutePath}")

    if (tempDir.exists()) {
        tempDir.deleteRecursively()
    }
    tempDir.mkdir()

    if (OperatingSystem.current().isWindows) {
        project.copy {
            it.from(project.tarTree(from))
            it.into(tempDir)
        }
    } else {
        project.exec {
            it.commandLine("tar", "-xpf", from.absolutePath, "--directory", tempDir.absolutePath)
        }
    }
    tempDir.renameTo(to)
}

fun isKotlinRuntime(name: String) = "kotlin-runtime" == name || "kotlin-reflect" == name || name.startsWith("kotlin-stdlib")
