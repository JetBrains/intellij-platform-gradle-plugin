@file:JvmName("Utils")

package org.jetbrains.intellij

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.base.utils.isJar
import com.jetbrains.plugin.structure.base.utils.isZip
import com.jetbrains.plugin.structure.intellij.beans.PluginBean
import com.jetbrains.plugin.structure.intellij.extractor.PluginBeanExtractor
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.AbstractFileFilter
import org.apache.commons.io.filefilter.FalseFileFilter
import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations
import org.gradle.process.JavaForkOptions
import org.jdom2.Document
import org.jdom2.JDOMException
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.jetbrains.intellij.dependency.IdeaDependency
import org.xml.sax.SAXParseException
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.StringWriter
import java.nio.file.Files.createTempDirectory
import java.util.Properties
import java.util.function.BiConsumer
import java.util.function.Predicate

val VERSION_PATTERN = "^([A-Z]+)-([0-9.A-z]+)\\s*$".toPattern()
val MAJOR_VERSION_PATTERN = "(RIDER-|GO-)?\\d{4}\\.\\d-(EAP\\d*-)?SNAPSHOT".toPattern()

@Suppress("DEPRECATION")
fun mainSourceSet(project: Project): SourceSet = project
    .convention.getPlugin(JavaPluginConvention::class.java)
//    .extensions.getByType(JavaPluginExtension::class.java) // available since Gradle 7.1
    .sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)

fun sourcePluginXmlFiles(project: Project) = mainSourceSet(project).resources.srcDirs.mapNotNull {
    File(it, "META-INF/plugin.xml").takeIf { file -> file.exists() && file.length() > 0 }
}

fun parsePluginXml(pluginXml: File, logCategory: String?): PluginBean? {
    try {
        val document = JDOMUtil.loadDocument(pluginXml.inputStream())
        return PluginBeanExtractor.extractPluginBean(document)
    } catch (e: SAXParseException) {
        warn(logCategory, "Cannot read: ${pluginXml.canonicalPath}. Skipping", e)
    } catch (e: JDOMException) {
        warn(logCategory, "Cannot read: ${pluginXml.canonicalPath}. Skipping", e)
    } catch (e: IOException) {
        warn(logCategory, "Cannot read: ${pluginXml.canonicalPath}. Skipping", e)
    }
    return null
}

fun transformXml(document: Document, file: File) {
    val xmlOutput = XMLOutputter()
    xmlOutput.format.apply {
        indent = "  "
        omitDeclaration = true
        textMode = Format.TextMode.TRIM
    }

    StringWriter().use {
        xmlOutput.output(document, it)
        file.writeText(it.toString())
    }
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

fun getIdeJvmArgs(options: JavaForkOptions, arguments: List<String>, ideDirectory: File?): List<String> {
    options.maxHeapSize = options.maxHeapSize ?: "512m"
    options.minHeapSize = options.minHeapSize ?: "256m"

    ideDirectory?.let {
        val bootJar = File(ideDirectory, "lib/boot.jar")
        if (bootJar.exists()) {
            return arguments + "-Xbootclasspath/a:${bootJar.absolutePath}"
        }
    }
    return arguments
}

fun ideBuildNumber(ideDirectory: File) = (
    File(ideDirectory, "Resources/build.txt").takeIf { OperatingSystem.current().isMacOsX && it.exists() }
        ?: File(ideDirectory, "build.txt")
    ).readText().trim()

fun ideaDir(path: String) = File(path).let {
    it.takeUnless { it.name.endsWith(".app") } ?: File(it, "Contents")
}

fun File.isJar() = toPath().isJar()

fun File.isZip() = toPath().isZip()

fun collectJars(directory: File, filter: Predicate<File>): Collection<File> = when {
    !directory.isDirectory -> emptyList()
    else -> FileUtils.listFiles(directory, object : AbstractFileFilter() {
        override fun accept(file: File) = file.isJar() && filter.test(file)
    }, FalseFileFilter.FALSE)
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

@Suppress("UnstableApiUsage")
@Incubating
fun extractArchive(
    archiveFile: File,
    targetDirectory: File,
    archiveOperations: ArchiveOperations,
    execOperations: ExecOperations,
    fileSystemOperations: FileSystemOperations,
    logCategory: String?,
    isUpToDate: Predicate<File>? = null,
    markUpToDate: BiConsumer<File, File>? = null,
): File {
    val name = archiveFile.name
    val markerFile = File(targetDirectory, "markerFile")
    if (markerFile.exists() && (isUpToDate == null || isUpToDate.test(markerFile))) {
        return targetDirectory
    }

    targetDirectory.deleteRecursively()
    targetDirectory.mkdirs()

    debug(logCategory, "Extracting: $name")

    if (name.endsWith(".tar.gz") && OperatingSystem.current().isWindows) {
        execOperations.exec {
            it.commandLine("tar", "-xpf", archiveFile.absolutePath, "--directory", targetDirectory.absolutePath)
        }
    } else {
        val decompressor = when {
            name.endsWith(".zip") || name.endsWith(".sit") -> archiveOperations::zipTree
            name.endsWith(".tar.gz") -> archiveOperations::tarTree
            else -> throw IllegalArgumentException("Unknown type archive type: $name")
        }
        fileSystemOperations.copy {
            it.from(decompressor.invoke(archiveFile))
            it.into(targetDirectory)
        }
    }

    debug(logCategory, "Extracted: $name")

    markerFile.createNewFile()
    markUpToDate?.accept(targetDirectory, markerFile)
    return targetDirectory
}

fun releaseType(version: String) = when {
    version.endsWith("-EAP-SNAPSHOT") ||
        version.endsWith("-EAP-CANDIDATE-SNAPSHOT") ||
        version.endsWith("-CUSTOM-SNAPSHOT") ||
        version.matches(MAJOR_VERSION_PATTERN.toRegex())
    -> "snapshots"
    version.endsWith("-SNAPSHOT") -> "nightly"
    else -> "releases"
}

fun error(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.ERROR, logCategory, message, e)
fun warn(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.WARN, logCategory, message, e)
fun info(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.INFO, logCategory, message, e)
fun debug(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.DEBUG, logCategory, message, e)

private fun log(level: LogLevel, logCategory: String?, message: String, e: Throwable?) {
    val category = "gradle-intellij-plugin ${logCategory ?: ""}".trim()
    val logger = Logging.getLogger(IntelliJPlugin::class.java)
    if (e != null && level != LogLevel.ERROR && !logger.isDebugEnabled) {
        logger.log(level, "[$category] $message. Run with --debug option to get more log output.")
    } else {
        logger.log(level, "[$category] $message", e)
    }
}

fun Project.logCategory(): String = path + name
fun Task.logCategory(): String = project.path + project.name + path

fun createPlugin(artifact: File, validatePluginXml: Boolean, context: String?): IdePlugin? {
    val extractDirectory = createTempDirectory("tmp")
    val creationResult = IdePluginManager.createManager(extractDirectory)
        .createPlugin(artifact.toPath(), validatePluginXml, IdePluginManager.PLUGIN_XML)

    return when (creationResult) {
        is PluginCreationSuccess -> creationResult.plugin
        is PluginCreationFail -> {
            val problems = creationResult.errorsAndWarnings.filter { it.level == PluginProblem.Level.ERROR }.joinToString()
            warn(context, "Cannot create plugin from file '$artifact': $problems")
            null
        }
        else -> {
            warn(context, "Cannot create plugin from file '$artifact'. $creationResult")
            null
        }
    }
}

fun isKotlinRuntime(name: String) =
    name == "kotlin-runtime" ||
        name == "kotlin-reflect" || name.startsWith("kotlin-reflect-") ||
        name == "kotlin-stdlib" || name.startsWith("kotlin-stdlib-") ||
        name == "kotlin-test" || name.startsWith("kotlin-test-")

fun DependencyHandler.create(
    group: String,
    name: String,
    version: String?,
    classifier: String? = null,
    extension: String? = null,
    configuration: String? = null,
): Dependency = create(mapOf(
    "group" to group,
    "name" to name,
    "version" to version,
    "classifier" to classifier,
    "ext" to extension,
    "configuration" to configuration,
))

fun isDependencyOnPyCharm(dependency: IdeaDependency): Boolean {
    return dependency.name == "pycharmPY" || dependency.name == "pycharmPC"
}

fun isPyCharmType(type: String): Boolean {
    return type == "PY" || type == "PC"
}

fun <T> T?.ifNull(block: () -> Unit): T? {
    if (this == null) {
        block()
    }
    return this
}

fun Boolean.ifFalse(block: () -> Unit): Boolean {
    if (!this) {
        block()
    }
    return this
}
