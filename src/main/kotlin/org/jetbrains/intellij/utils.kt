@file:JvmName("Utils")

package org.jetbrains.intellij

import com.ctc.wstx.stax.WstxInputFactory
import com.ctc.wstx.stax.WstxOutputFactory
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.base.utils.isJar
import com.jetbrains.plugin.structure.base.utils.isZip
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.AbstractFileFilter
import org.apache.commons.io.filefilter.FalseFileFilter
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations
import org.gradle.process.JavaForkOptions
import org.jetbrains.intellij.model.IdeaPlugin
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.SAXException
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.util.Properties
import java.util.function.BiConsumer
import java.util.function.Predicate
import javax.xml.parsers.DocumentBuilderFactory

val VERSION_PATTERN = "^([A-Z]+)-([0-9.A-z]+)\\s*$".toPattern()
val MAJOR_VERSION_PATTERN = "(RIDER-)?\\d{4}\\.\\d-SNAPSHOT".toPattern()

fun mainSourceSet(project: Project): SourceSet {
    val javaConvention = project.convention.getPlugin(JavaPluginConvention::class.java)
    return javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
}

fun sourcePluginXmlFiles(project: Project): List<File> {
    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    return mainSourceSet(project).resources.srcDirs.mapNotNull {
        val pluginXml = File(it, "META-INF/plugin.xml")
        try {
            return@mapNotNull pluginXml.takeIf {
                val document = builder.parse(pluginXml)
                document.childNodes.asSequence().any { node ->
                    node.nodeName == "idea-plugin"
                }
            }
        } catch (e: SAXException) {
            warn(project, "Cannot read ${pluginXml.canonicalPath}. Skipping", e)
        } catch (e: IOException) {
            warn(project, "Cannot read ${pluginXml.canonicalPath}. Skipping", e)
        }
        null
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

fun getPluginIds(project: Project) = sourcePluginXmlFiles(project).mapNotNull {
    parseXml(it, IdeaPlugin::class.java).id
}

fun <T> parseXml(stream: InputStream, valueType: Class<T>): T = XmlMapper()
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .readValue(stream, valueType)

fun <T> parseXml(file: File, valueType: Class<T>) = parseXml(file.inputStream(), valueType)

fun writeXml(file: File, value: Any) {
    XmlMapper(XmlFactory(WstxInputFactory(), WstxOutputFactory()))
        .registerKotlinModule()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .writerWithDefaultPrettyPrinter()
        .writeValue(file, value)
}

fun NodeList.asSequence() = (0 until length).asSequence().map { item(it) }

fun Node.get(name: String) = childNodes.asSequence().find { it.nodeName == name }

fun Node.attribute(name: String) = attributes.getNamedItem(name)?.textContent

fun isJarFile(file: File) = file.toPath().isJar()

fun isZipFile(file: File) = file.toPath().isZip()

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

@Suppress("UnstableApiUsage")
fun unzip(
    zipFile: File,
    directory: File,
    archiveOperations: ArchiveOperations,
    fileSystemOperations: FileSystemOperations,
    context: Any,
    isUpToDate: Predicate<File>? = null,
    markUpToDate: BiConsumer<File, File>? = null,
    targetDirName: String? = null,
): File {
    val targetDirectory = File(directory, targetDirName ?: zipFile.name.removeSuffix(".zip"))
    val markerFile = File(targetDirectory, "markerFile")
    if (markerFile.exists() && (isUpToDate == null || isUpToDate.test(markerFile))) {
        return targetDirectory
    }

    fileSystemOperations.delete {
        it.delete(targetDirectory)
    }

    debug(context, "Unzipping ${zipFile.name}")
    fileSystemOperations.copy {
        it.from(archiveOperations.zipTree(zipFile))
        it.into(targetDirectory)
    }

    debug(context, "Unzipped ${zipFile.name}")
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

fun error(context: Any? = null, message: String, e: Throwable? = null) = log(LogLevel.ERROR, context, message, e)
fun warn(context: Any? = null, message: String, e: Throwable? = null) = log(LogLevel.WARN, context, message, e)
fun info(context: Any? = null, message: String, e: Throwable? = null) = log(LogLevel.INFO, context, message, e)
fun debug(context: Any? = null, message: String, e: Throwable? = null) = log(LogLevel.DEBUG, context, message, e)

private fun log(level: LogLevel, context: Any?, message: String, e: Throwable?) {
    val category = when (context) {
        is Project -> "gradle-intellij-plugin :${context.path}"
        is Task -> "gradle-intellij-plugin :${context.path}"
        else -> "gradle-intellij-plugin"
    }
    val logger = Logging.getLogger(IntelliJPlugin::class.java)
    if (e != null && level != LogLevel.ERROR && !logger.isDebugEnabled) {
        logger.log(level, "[$category] $message. Run with --debug option to get more log output.")
    } else {
        logger.log(level, "[$category] $message", e)
    }
}

fun createPlugin(artifact: File, validatePluginXml: Boolean, context: Any) =
    when (val result = IdePluginManager.createManager().createPlugin(artifact.toPath(), validatePluginXml, IdePluginManager.PLUGIN_XML)) {
        is PluginCreationSuccess -> result.plugin
        is PluginCreationFail -> {
            val problems = result.errorsAndWarnings.filter { it.level == PluginProblem.Level.ERROR }.joinToString()
            warn(context, "Cannot create plugin from file ($artifact): $problems")
            null
        }
        else -> {
            warn(context, "Cannot create plugin from file ($artifact). $result")
            null
        }
    }

@Suppress("UnstableApiUsage")
fun untar(
    from: File,
    to: File,
    archiveOperations: ArchiveOperations,
    execOperations: ExecOperations,
    fileSystemOperations: FileSystemOperations,
    context: Any,
) {
    val tempDir = File(to.parent, to.name + "-temp")
    debug(context, "Unpacking ${from.absolutePath} to ${tempDir.absolutePath}")

    if (tempDir.exists()) {
        tempDir.deleteRecursively()
    }
    tempDir.mkdir()

    if (OperatingSystem.current().isWindows) {
        fileSystemOperations.copy {
            it.from(archiveOperations.tarTree(from))
            it.into(tempDir)
        }
    } else {
        execOperations.exec {
            it.commandLine("tar", "-xpf", from.absolutePath, "--directory", tempDir.absolutePath)
        }
    }
    tempDir.renameTo(to)
}

fun isKotlinRuntime(name: String) = "kotlin-runtime" == name ||
    name == "kotlin-reflect" || name.startsWith("kotlin-reflect-") ||
    name == "kotlin-stdlib" || name.startsWith("kotlin-stdlib-") ||
    name == "kotlin-test" || name.startsWith("kotlin-test-")
