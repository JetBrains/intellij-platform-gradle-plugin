// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("Utils")
@file:Suppress("DEPRECATION", "BooleanMethodIsAlwaysInverted")

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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.AbstractFileFilter
import org.apache.commons.io.filefilter.FalseFileFilter
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.getPlugin
import org.gradle.process.JavaForkOptions
import org.jdom2.Document
import org.jdom2.JDOMException
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_PYCHARM
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_PYCHARM_COMMUNITY
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_SUFFIX_CUSTOM_SNAPSHOT
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_SUFFIX_EAP
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_SUFFIX_EAP_CANDIDATE
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_SUFFIX_SNAPSHOT
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_TYPE_NIGHTLY
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_TYPE_RELEASES
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_TYPE_SNAPSHOTS
import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.model.ProductInfo
import org.jetbrains.intellij.utils.OpenedPackages
import org.xml.sax.SAXParseException
import java.io.File
import java.io.IOException
import java.io.StringWriter
import java.nio.file.Files.createTempDirectory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.function.Predicate

private val MAJOR_VERSION_PATTERN = "(RIDER-|GO-)?\\d{4}\\.\\d-(EAP\\d*-)?SNAPSHOT".toPattern()

@Suppress("DEPRECATION")
internal fun mainSourceSet(project: Project): SourceSet = project
    .convention.getPlugin<JavaPluginConvention>()
//    .extensions.getByType<JavaPluginConvention>() // available since Gradle 7.1
    .sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)

internal fun sourcePluginXmlFiles(project: Project) = mainSourceSet(project).resources.srcDirs.mapNotNull {
    File(it, "META-INF/plugin.xml").takeIf { file -> file.exists() && file.length() > 0 }
}

internal fun parsePluginXml(pluginXml: File, logCategory: String?): PluginBean? {
    try {
        pluginXml.inputStream().use {
            val document = JDOMUtil.loadDocument(it)
            return PluginBeanExtractor.extractPluginBean(document)
        }
    } catch (e: SAXParseException) {
        warn(logCategory, "Cannot read: ${pluginXml.canonicalPath}. Skipping", e)
    } catch (e: JDOMException) {
        warn(logCategory, "Cannot read: ${pluginXml.canonicalPath}. Skipping", e)
    } catch (e: IOException) {
        warn(logCategory, "Cannot read: ${pluginXml.canonicalPath}. Skipping", e)
    }
    return null
}

internal fun transformXml(document: Document, file: File) {
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

internal fun getIdeaSystemProperties(
    ideDirFile: File,
    configDirectory: File,
    systemDirectory: File,
    pluginsDirectory: File,
    requirePluginIds: List<String>,
): Map<String, String> {
    val currentLaunch = ideProductInfo(ideDirFile)?.currentLaunch
    val result = mapOf(
        "idea.config.path" to configDirectory.canonicalPath,
        "idea.system.path" to systemDirectory.canonicalPath,
        "idea.plugins.path" to pluginsDirectory.canonicalPath,
    )

    val currentLaunchProperties = currentLaunch
        ?.additionalJvmArguments
        ?.filter {
            it.startsWith("-D")
        }
        ?.associate {
            it
                .substring(2)
                .split('=')
                .let { (key, value) ->
                    key to value
                        .replace("\$APP_PACKAGE", ideDirFile.canonicalPath)
                        .replace("\$IDE_HOME", ideDirFile.canonicalPath)
                        .replace("%IDE_HOME%", ideDirFile.canonicalPath)
                }
        }
        ?: emptyMap()

    val requirePluginProperties = requirePluginIds
        .takeIf(List<String>::isNotEmpty)
        ?.let { mapOf("idea.required.plugins.id" to it.joinToString(",")) }
        ?: emptyMap()

    return result + currentLaunchProperties + requirePluginProperties
}

internal fun getIdeJvmArgs(options: JavaForkOptions, arguments: List<String>?, ideDirectory: File?): List<String> {
    with(options) {
        maxHeapSize = maxHeapSize ?: "512m"
        minHeapSize = minHeapSize ?: "256m"
    }

    val productInfo = ideProductInfo(ideDirectory!!)
    val bootclasspath = ideDirectory
        .resolve("lib/boot.jar")
        .takeIf { it.exists() }
        ?.let { listOf("-Xbootclasspath/a:${it.canonicalPath}") }
        .orEmpty()
    val vmOptions = productInfo?.currentLaunch?.vmOptionsFilePath
        ?.let { ideDirectory.resolve(it).readLines() }
        .orEmpty()
    val additionalJvmArguments = productInfo?.currentLaunch?.additionalJvmArguments
        ?.filterNot { it.startsWith("-D") }
        ?.takeIf { it.isNotEmpty() }
        ?: OpenedPackages

    return arguments.orEmpty() + bootclasspath + vmOptions + additionalJvmArguments
}

internal fun ideBuildNumber(ideDirectory: File) = (
        File(ideDirectory, "Resources/build.txt").takeIf { OperatingSystem.current().isMacOsX && it.exists() }
            ?: File(ideDirectory, "build.txt")
        ).readText().trim()

private val json = Json { ignoreUnknownKeys = true }

internal fun ideProductInfo(ideDirectory: File) = (
        File(ideDirectory, "Resources/product-info.json").takeIf { OperatingSystem.current().isMacOsX && it.exists() }
            ?: File(ideDirectory, "product-info.json")
        )
    .runCatching { json.decodeFromString<ProductInfo>(readText()) }
    .getOrNull()

internal fun ideaDir(path: String) = File(path).let {
    it.takeUnless { it.name.endsWith(".app") } ?: File(it, "Contents")
}

internal fun collectJars(directory: File, filter: Predicate<File> = Predicate { true }): Collection<File> =
    collectFiles(directory) { it.toPath().isJar() && filter.test(it) }

internal fun collectZips(directory: File, filter: Predicate<File> = Predicate { true }): Collection<File> =
    collectFiles(directory) { it.toPath().isZip() && filter.test(it) }

private fun collectFiles(directory: File, filter: Predicate<File>): Collection<File> = when {
    !directory.isDirectory -> emptyList()
    else -> FileUtils.listFiles(directory, object : AbstractFileFilter() {
        override fun accept(file: File) = filter.test(file)
    }, FalseFileFilter.FALSE)
}

internal fun releaseType(version: String) = when {
    version.endsWith(RELEASE_SUFFIX_EAP) ||
            version.endsWith(RELEASE_SUFFIX_EAP_CANDIDATE) ||
            version.endsWith(RELEASE_SUFFIX_CUSTOM_SNAPSHOT) ||
            version.matches(MAJOR_VERSION_PATTERN.toRegex())
    -> RELEASE_TYPE_SNAPSHOTS

    version.endsWith(RELEASE_SUFFIX_SNAPSHOT) -> RELEASE_TYPE_NIGHTLY
    else -> RELEASE_TYPE_RELEASES
}

internal fun error(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.ERROR, logCategory, message, e)
internal fun warn(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.WARN, logCategory, message, e)
internal fun info(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.INFO, logCategory, message, e)
internal fun debug(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.DEBUG, logCategory, message, e)

private fun log(level: LogLevel, logCategory: String?, message: String, e: Throwable?) {
    val category = "gradle-intellij-plugin ${logCategory.orEmpty()}".trim()
    val logger = Logging.getLogger(IntelliJPlugin::class.java)
    if (e != null && level != LogLevel.ERROR && !logger.isDebugEnabled) {
        logger.log(level, "[$category] $message. Run with --debug option to get more log output.")
    } else {
        logger.log(level, "[$category] $message", e)
    }
}

internal fun Project.logCategory(): String = path + name.takeIf { ":$it" != path }.orEmpty()

internal fun Task.logCategory(): String = project.logCategory() + path.removePrefix(project.logCategory())

internal fun createPlugin(artifact: File, validatePluginXml: Boolean, context: String?): IdePlugin? {
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

internal fun isKotlinRuntime(name: String) =
    name == "kotlin-runtime" ||
            name == "kotlin-reflect" || name.startsWith("kotlin-reflect-") ||
            name == "kotlin-stdlib" || name.startsWith("kotlin-stdlib-") ||
            name == "kotlin-test" || name.startsWith("kotlin-test-")

internal fun isDependencyOnPyCharm(dependency: IdeaDependency) = dependency.name == "pycharmPY" || dependency.name == "pycharmPC"

internal fun isPyCharmType(type: String) = type == PLATFORM_TYPE_PYCHARM || type == PLATFORM_TYPE_PYCHARM_COMMUNITY

internal val repositoryVersion: String by lazy {
    LocalDateTime.now().format(
        DateTimeFormatterBuilder()
            .append(DateTimeFormatter.BASIC_ISO_DATE)
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .toFormatter()
    )
}

internal fun <T> T?.ifNull(block: () -> Unit): T? {
    if (this == null) {
        block()
    }
    return this
}

internal fun Boolean.ifFalse(block: () -> Unit): Boolean {
    if (!this) {
        block()
    }
    return this
}
