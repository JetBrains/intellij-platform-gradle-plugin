// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("Utils")
@file:Suppress("BooleanMethodIsAlwaysInverted")

package org.jetbrains.intellij.platform.gradleplugin

import com.dd.plist.NSDictionary
import com.dd.plist.PropertyListParser
import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.base.utils.*
import com.jetbrains.plugin.structure.intellij.extractor.PluginBeanExtractor
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import kotlinx.serialization.json.Json
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.getByName
import org.gradle.util.GradleVersion
import org.jdom2.Document
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.MINIMAL_SUPPORTED_GRADLE_VERSION
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLATFORM_TYPE_PYCHARM
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLATFORM_TYPE_PYCHARM_COMMUNITY
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.RELEASE_SUFFIX_CUSTOM_SNAPSHOT
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.RELEASE_SUFFIX_EAP
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.RELEASE_SUFFIX_EAP_CANDIDATE
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.RELEASE_SUFFIX_SNAPSHOT
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.RELEASE_TYPE_NIGHTLY
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.RELEASE_TYPE_RELEASES
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.RELEASE_TYPE_SNAPSHOTS
import org.jetbrains.intellij.platform.gradleplugin.dependency.IdeaDependency
import org.jetbrains.intellij.platform.gradleplugin.model.ProductInfo
import org.jetbrains.intellij.platform.gradleplugin.plugins.IntelliJPlatformPlugin
import java.io.File
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files.createTempDirectory
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.function.Predicate
import java.util.jar.Manifest

val MAJOR_VERSION_PATTERN = "(RIDER-|GO-)?\\d{4}\\.\\d-(EAP\\d*-)?SNAPSHOT".toPattern()

internal fun sourcePluginXmlFiles(project: Project) = project
    .extensions.getByName<JavaPluginExtension>("java") // Name hard-coded in JavaBasePlugin.addExtensions and well-known.
    .sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME) // TODO: iterate over all sourceSets?
    .resources
    .srcDirs
    .filterNotNull()
    .map(File::toPath)
    .map { it.resolve("META-INF/plugin.xml") }
    .filter { it.exists() && it.length > 0 }

internal fun parsePluginXml(pluginXml: Path, logCategory: String?) = runCatching {
    pluginXml.inputStream().use {
        val document = JDOMUtil.loadDocument(it)
        PluginBeanExtractor.extractPluginBean(document)
    }
}.getOrElse {
    warn(logCategory, "Cannot read: $pluginXml. Skipping", it)
    null
}

fun transformXml(document: Document, path: Path) {
    val xmlOutput = XMLOutputter()
    xmlOutput.format.apply {
        indent = "  "
        omitDeclaration = true
        textMode = Format.TextMode.TRIM
    }

    StringWriter().use {
        xmlOutput.output(document, it)
        path.writeText(it.toString())
    }
}

internal fun String.resolveIdeHomeVariable(ideDir: Path) =
    ideDir.toAbsolutePath().toString().let { idePath ->
        this
            .replace("\$APP_PACKAGE", idePath)
            .replace("\$IDE_HOME", idePath)
            .replace("%IDE_HOME%", idePath)
            .replace("Contents/Contents", "Contents")
            .let { entry ->
                val (_, value) = entry.split("=")
                when {
                    Path.of(value).exists() -> entry
                    else -> entry.replace("/Contents", "")
                }
            }
    }

fun getIdeaClasspath(ideDir: Path): List<String> {
    val buildNumber = ideBuildNumber(ideDir).split('-').last().let { Version.parse(it) }
    val build203 = Version.parse("203.0")
    val build221 = Version.parse("221.0")
    val build223 = Version.parse("223.0")

    val currentLaunch = ideProductInfo(ideDir)?.currentLaunch
    val infoPlist = ideDir.resolve("Info.plist").takeIf(Path::exists)?.let {
        PropertyListParser.parse(it) as NSDictionary
    }

    return when {
        buildNumber > build223 ->
            currentLaunch
                ?.bootClassPathJarNames
                ?: infoPlist
                    ?.getDictionary("JVMOptions")
                    ?.getValue("ClassPath")
                    ?.split(':')
                    ?.map { it.removePrefix("\$APP_PACKAGE/Contents/lib/") }
                    .orEmpty()

        buildNumber > build221 -> listOf(
            "3rd-party-rt.jar",
            "util.jar",
            "util_rt.jar",
            "jna.jar",
        )

        buildNumber > build203 -> listOf(
            "bootstrap.jar",
            "util.jar",
            "jdom.jar",
            "log4j.jar",
            "jna.jar",
        )

        else -> listOf(
            "bootstrap.jar",
            "extensions.jar",
            "util.jar",
            "jdom.jar",
            "log4j.jar",
            "jna.jar",
            "trove4j.jar",
        )
    }.map { "$ideDir/lib/$it" }
}

fun ideBuildNumber(ideDir: Path) = ideDir
    .resolve("Resources/build.txt")
    .takeIf { OperatingSystem.current().isMacOsX && it.exists() }
    .or { ideDir.resolve("build.txt") }
    .readText().trim()

private val json = Json { ignoreUnknownKeys = true }
fun ideProductInfo(ideDir: Path) = ideDir
    .resolve("Resources/product-info.json")
    .takeIf { OperatingSystem.current().isMacOsX && it.exists() }
    .or { ideDir.resolve("product-info.json") }
    .runCatching { json.decodeFromString<ProductInfo>(readText()) }
    .getOrNull()

fun collectJars(directory: Path, filter: Predicate<Path> = Predicate { true }) =
    collectFiles(directory) { it.isJar() && filter.test(it) }

fun collectZips(directory: Path, filter: Predicate<Path> = Predicate { true }) =
    collectFiles(directory) { it.isZip() && filter.test(it) }

private fun collectFiles(directory: Path, filter: Predicate<Path>) = directory
    .takeIf { it.isDirectory }
    ?.listFiles()
    .orEmpty()
    .filter { filter.test(it) }

fun releaseType(version: String) = when {
    version.endsWith(RELEASE_SUFFIX_EAP) ||
            version.endsWith(RELEASE_SUFFIX_EAP_CANDIDATE) ||
            version.endsWith(RELEASE_SUFFIX_CUSTOM_SNAPSHOT) ||
            version.matches(MAJOR_VERSION_PATTERN.toRegex())
    -> RELEASE_TYPE_SNAPSHOTS

    version.endsWith(RELEASE_SUFFIX_SNAPSHOT) -> RELEASE_TYPE_NIGHTLY
    else -> RELEASE_TYPE_RELEASES
}

fun error(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.ERROR, logCategory, message, e)
fun warn(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.WARN, logCategory, message, e)
fun info(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.INFO, logCategory, message, e)
fun debug(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.DEBUG, logCategory, message, e)

private val logger = Logging.getLogger(IntelliJPlatformPlugin::class.java)

private fun log(level: LogLevel, logCategory: String?, message: String, e: Throwable?) {
    val category = "gradle-intellij-plugin ${logCategory.orEmpty()}".trim()
    if (e != null && level != LogLevel.ERROR && !logger.isDebugEnabled) {
        logger.log(level, "[$category] $message. Run with --debug option to get more log output.")
    } else {
        logger.log(level, "[$category] $message", e)
    }
}

fun Project.logCategory(): String = path + if (path.endsWith(name)) " $name" else ""

fun Task.logCategory(): String = project.logCategory() + path.removePrefix(project.logCategory())

fun createPlugin(artifact: Path, validatePluginXml: Boolean, context: String?): IdePlugin? {
    val extractDirectory = createTempDirectory("tmp")
    val creationResult = IdePluginManager.createManager(extractDirectory)
        .createPlugin(artifact, validatePluginXml, IdePluginManager.PLUGIN_XML)

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

fun isDependencyOnPyCharm(dependency: IdeaDependency) = dependency.name == "pycharmPY" || dependency.name == "pycharmPC"

fun isPyCharmType(type: String) = type == PLATFORM_TYPE_PYCHARM || type == PLATFORM_TYPE_PYCHARM_COMMUNITY

val repositoryVersion: String by lazy {
    LocalDateTime.now().format(
        DateTimeFormatterBuilder()
            .append(DateTimeFormatter.BASIC_ISO_DATE)
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .toFormatter()
    )
}

fun <T> T?.or(other: T): T = this ?: other

fun <T> T?.or(block: () -> T): T = this ?: block()

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

fun NSDictionary.getDictionary(key: String) = this[key] as? NSDictionary

fun NSDictionary.getValue(key: String) = this[key]?.toString()

internal val FileSystemLocation.asPath
    get() = asFile.toPath().toAbsolutePath()

internal fun URL.resolveRedirection() = with(openConnection() as HttpURLConnection) {
    instanceFollowRedirects = false
    inputStream.use {
        when (responseCode) {
            HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP -> URL(this@resolveRedirection, getHeaderField("Location"))
            else -> this@resolveRedirection
        }
    }.also { disconnect() }
}

internal fun Project.checkGradleVersion() {
    if (GradleVersion.current() < GradleVersion.version(MINIMAL_SUPPORTED_GRADLE_VERSION)) {
        throw PluginInstantiationException("$PLUGIN_NAME requires Gradle $MINIMAL_SUPPORTED_GRADLE_VERSION and higher")
    }
}

internal fun getCurrentPluginVersion() = IntelliJPlatformPlugin::class.java
    .run { getResource("$simpleName.class") }
    .runCatching {
        val manifestPath = with(this?.path) {
            when {
                this == null -> return@runCatching null
                startsWith("jar:") -> this
                startsWith("file:") -> "jar:$this"
                else -> return@runCatching null
            }
        }.run { substring(0, lastIndexOf("!") + 1) } + "/META-INF/MANIFEST.MF"
        info(null, "Resolving IntelliJ Platform Gradle Plugin version with: $manifestPath")
        URL(manifestPath).openStream().use {
            Manifest(it).mainAttributes.getValue("Version")
        }
    }.getOrNull()

internal val <T> Property<T>.isSpecified: Boolean
    get() = isPresent && when(val value = orNull) {
        null -> false
        is String -> value.isNotEmpty()
        is RegularFile -> value.asFile.exists()
        else -> true
    }

