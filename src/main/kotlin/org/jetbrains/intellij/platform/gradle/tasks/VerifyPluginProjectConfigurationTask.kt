// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.base.utils.exists
import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.kotlin.dsl.withType
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Constraints.MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER
import org.jetbrains.intellij.platform.gradle.Constants.Constraints.MINIMAL_INTELLIJ_PLATFORM_VERSION
import org.jetbrains.intellij.platform.gradle.Constants.GradleProperties
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatformCachePath
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.PluginAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.parse
import org.jetbrains.intellij.platform.gradle.utils.*
import java.io.File
import kotlin.io.path.readLines
import kotlin.io.path.writeText

/**
 * Validates the plugin project configuration:
 *
 * - The [PatchPluginXmlTask.sinceBuild] property can't be lower than the target IntelliJ Platform major version.
 * - The Java/Kotlin `sourceCompatibility` and `targetCompatibility` properties should align Java versions required by [PatchPluginXmlTask.sinceBuild] and the currently used IntelliJ Platform.
 * - The Kotlin API version should align the version required by [PatchPluginXmlTask.sinceBuild] and the currently used IntelliJ Platform.
 * - The used IntelliJ Platform version must be equal or higher than the minimum supported version `2022.3` (`223`) defined in [MINIMAL_INTELLIJ_PLATFORM_VERSION].
 * - The dependency on the [Kotlin Standard Library](https://jb.gg/intellij-platform-kotlin-stdlib) should be excluded.
 * - The Kotlin Coroutines library [must not be added explicitly](https://jb.gg/intellij-platform-kotlin-coroutines) to the project as it is already provided with the IntelliJ Platform.
 *
 * @see <a href="https://jb.gg/intellij-platform-versions">Build Number Ranges</a>
 */
// TODO: Use Reporting for handling verification report output? https://docs.gradle.org/current/dsl/org.gradle.api.reporting.Reporting.html
@CacheableTask
abstract class VerifyPluginProjectConfigurationTask : DefaultTask(), IntelliJPlatformVersionAware, PluginAware {

    /**
     * Report directory where the verification result will be stored.
     */
    @get:OutputDirectory
    abstract val reportDirectory: DirectoryProperty

    /**
     * Root project path.
     */
    @get:Internal
    abstract val rootDirectory: Property<File>

    /**
     * IntelliJ Platform cache directory.
     */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val intellijPlatformCache: DirectoryProperty

    /**
     * The `.gitignore` file located in the [rootDirectory], tracked for content change.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val gitignoreFile: RegularFileProperty

    /**
     * [JavaCompile.sourceCompatibility] property defined in the build script.
     */
    @get:Internal
    abstract val sourceCompatibility: Property<String>

    /**
     * [JavaCompile.targetCompatibility] property defined in the build script.
     */
    @get:Internal
    abstract val targetCompatibility: Property<String>

    /**
     * Indicates that the Kotlin Gradle Plugin is loaded and available.
     */
    @get:Internal
    abstract val kotlinPluginAvailable: Property<Boolean>

    /**
     * The `apiVersion` property value of `compileKotlin.kotlinOptions` defined in the build script.
     */
    @get:Internal
    abstract val kotlinApiVersion: Property<String?>

    /**
     * The `languageVersion` property value of `compileKotlin.kotlinOptions` defined in the build script.
     */
    @get:Internal
    abstract val kotlinLanguageVersion: Property<String?>

    /**
     * The version of Kotlin used in the project.
     */
    @get:Internal
    abstract val kotlinVersion: Property<String?>

    /**
     * The `jvmTarget` property value of `compileKotlin.kotlinOptions` defined in the build script.
     */
    @get:Internal
    abstract val kotlinJvmTarget: Property<String?>

    /**
     * `kotlin.stdlib.default.dependency` property value defined in the `gradle.properties` file.
     */
    @get:Internal
    abstract val kotlinStdlibDefaultDependency: Property<Boolean>

    /**
     * This variable represents whether the Kotlin Coroutines library is added explicitly to the project dependencies.
     */
    @get:Internal
    abstract val kotlinxCoroutinesLibraryPresent: Property<Boolean>

    private val log = Logger(javaClass)

    init {
        group = Plugin.GROUP_NAME
        description = "Validates the plugin project configuration"
    }

    @TaskAction
    fun verifyPluginConfiguration() {
        val platformBuild = productInfo.buildNumber.toVersion()
        val platformVersion = productInfo.version.toVersion()
        val platformJavaVersion = getPlatformJavaVersion(platformBuild)
        val sourceCompatibilityJavaVersion = JavaVersion.toVersion(sourceCompatibility.get())
        val targetCompatibilityJavaVersion = JavaVersion.toVersion(targetCompatibility.get())
        val jvmTargetJavaVersion = kotlinJvmTarget.orNull?.let { JavaVersion.toVersion(it) }
        val kotlinApiVersion = kotlinApiVersion.orNull?.toVersion()
        val kotlinLanguageVersion = kotlinLanguageVersion.orNull?.toVersion()
        val kotlinPluginAvailable = kotlinPluginAvailable.get()
        val kotlinStdlibDefaultDependency = kotlinStdlibDefaultDependency.orNull != false
        val kotlinxCoroutinesLibraryPresent = kotlinxCoroutinesLibraryPresent.get()
        val platformKotlinLanguageVersion = getPlatformKotlinVersion(platformBuild)?.run { "$major.$minor".toVersion() }

        sequence {
            pluginXml.orNull
                ?.let { file ->
                    val sinceBuild = file.parse { ideaVersion.sinceBuild.toVersion() }
                    val sinceBuildJavaVersion = getPlatformJavaVersion(sinceBuild)
                    val sinceBuildKotlinApiVersion =
                        getPlatformKotlinVersion(sinceBuild)?.run { "$major.$minor".toVersion() }

                    if (sinceBuild.version.contains('*')) {
                        yield("The since-build='$sinceBuild' should not contain wildcard.")
                    }
                    if (sinceBuild.major < platformBuild.major) {
                        yield("The since-build='$sinceBuild' is lower than the target IntelliJ Platform major version: '${platformBuild.major}'.")
                    }
                    if (sinceBuildJavaVersion < targetCompatibilityJavaVersion) {
                        yield("The Java configuration specifies targetCompatibility=$targetCompatibilityJavaVersion but since-build='$sinceBuild' property requires targetCompatibility='$sinceBuildJavaVersion'.")
                    }
                    if (sinceBuildJavaVersion < jvmTargetJavaVersion) {
                        yield("The Kotlin configuration specifies jvmTarget='$jvmTargetJavaVersion' but since-build='$sinceBuild' property requires jvmTarget='$sinceBuildJavaVersion'.")
                    }
                    if (sinceBuildKotlinApiVersion < kotlinApiVersion) {
                        yield("The Kotlin configuration specifies apiVersion='$kotlinApiVersion' but since-build='$sinceBuild' property requires apiVersion='$sinceBuildKotlinApiVersion'.")
                    }
                }
                ?: yield("The plugin.xml descriptor file could not be found.")


            if (platformBuild < MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER) {
                yield("The minimal supported IntelliJ Platform version is `$MINIMAL_INTELLIJ_PLATFORM_VERSION` (branch `$MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER`), current: '$platformVersion' ('$platformBuild')")
            }
            if (platformJavaVersion > sourceCompatibilityJavaVersion) {
                yield("The Java configuration specifies sourceCompatibility='$sourceCompatibilityJavaVersion' but IntelliJ Platform '$platformVersion' requires sourceCompatibility='$platformJavaVersion'.")
            }
            if (platformKotlinLanguageVersion > kotlinLanguageVersion) {
                yield("The Kotlin configuration specifies languageVersion='$kotlinLanguageVersion' but IntelliJ Platform '$platformVersion' requires languageVersion='$platformKotlinLanguageVersion'.")
            }
            if (platformJavaVersion < targetCompatibilityJavaVersion) {
                yield("The Java configuration specifies targetCompatibility='$targetCompatibilityJavaVersion' but IntelliJ Platform '$platformVersion' requires targetCompatibility='$platformJavaVersion'.")
            }
            if (platformJavaVersion < jvmTargetJavaVersion) {
                yield("The Kotlin configuration specifies jvmTarget='$jvmTargetJavaVersion' but IntelliJ Platform '$platformVersion' requires jvmTarget='$platformJavaVersion'.")
            }
            if (kotlinPluginAvailable && kotlinStdlibDefaultDependency) {
                yield("The dependency on the Kotlin Standard Library (stdlib) is automatically added when using the Gradle Kotlin plugin and may conflict with the version provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-stdlib")
            }
            if (kotlinxCoroutinesLibraryPresent) {
                yield("The Kotlin Coroutines library must not be added explicitly to the project as it is already provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-coroutines")
            }
            run {
                val gitignore = gitignoreFile.orNull?.asPath ?: return@run
                val cache = intellijPlatformCache.asPath.takeIf { it.exists() } ?: return@run
                val root = rootDirectory.get().toPath()

                if (cache != root.resolve(CACHE_DIRECTORY)) {
                    return@run
                }

                val containsEntry = gitignore.readLines().any { line -> line.contains(CACHE_DIRECTORY) }
                if (!containsEntry) {
                    this@sequence.yield("The IntelliJ Platform cache directory should be excluded from the version control system. Add the '$CACHE_DIRECTORY' entry to the '.gitignore' file.")
                }
            }
        }
            .joinToString(System.lineSeparator()) { "- $it" }
            .takeIf { it.isNotEmpty() }
            ?.also {
                log.warn(
                    listOf(
                        "The following plugin configuration issues were found:",
                        it,
                        "See: https://jb.gg/intellij-platform-versions"
                    ).joinToString(System.lineSeparator())
                )
            }
            .also {
                reportDirectory.file("report.txt").asPath.writeText(it.orEmpty())
            }
    }

    private fun getPlatformJavaVersion(buildNumber: Version) =
        PlatformJavaVersions.entries.firstOrNull { buildNumber >= it.key }?.value

    private fun getPlatformKotlinVersion(buildNumber: Version) =
        PlatformKotlinVersions.entries.firstOrNull { buildNumber >= it.key }?.value

    private operator fun JavaVersion?.compareTo(other: JavaVersion?) = other?.let { this?.compareTo(it) } ?: 0

    private operator fun Version?.compareTo(other: Version?) = other?.let { this?.compareTo(it) } ?: 0

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<VerifyPluginProjectConfigurationTask>(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
                log.info("Configuring plugin configuration verification task")

                val compileJavaTaskProvider = project.tasks.named<JavaCompile>(Tasks.External.COMPILE_JAVA)

                reportDirectory.convention(project.layout.buildDirectory.dir("reports/verifyPluginConfiguration"))

                rootDirectory.convention(project.provider {
                    project.rootProject.rootDir
                })
                intellijPlatformCache.convention(project.layout.dir(project.provider {
                    project.providers.intellijPlatformCachePath(project.rootProject.rootDir.toPath()).toFile()
                }))
                gitignoreFile.convention(project.layout.file(project.provider {
                    project.rootProject.rootDir.resolve(".gitignore").takeIf { it.exists() }
                }))

                sourceCompatibility.convention(compileJavaTaskProvider.map {
                    it.sourceCompatibility
                })
                targetCompatibility.convention(compileJavaTaskProvider.map {
                    it.targetCompatibility
                })
                kotlinxCoroutinesLibraryPresent.convention(project.provider {
                    listOf(
                        Configurations.External.IMPLEMENTATION,
                        Configurations.External.COMPILE_ONLY,
                    ).any { configurationName ->
                        project.configurations[configurationName].dependencies.any {
                            it.group == "org.jetbrains.kotlinx" && it.name.startsWith("kotlinx-coroutines")
                        }
                    }
                })

                kotlinPluginAvailable.convention(project.provider {
                    project.pluginManager.hasPlugin(Plugins.External.KOTLIN)
                })
                project.pluginManager.withPlugin(Plugins.External.KOTLIN) {
                    val kotlinOptionsProvider = project.tasks.named(Tasks.External.COMPILE_KOTLIN).apply {
                        configure {
                            dependsOn(this@registerTask)
                        }
                    }.map {
                        it.withGroovyBuilder { getProperty("kotlinOptions") }
                            .withGroovyBuilder { getProperty("options") }
                    }

                    kotlinJvmTarget.convention(kotlinOptionsProvider.flatMap {
                        it.withGroovyBuilder { getProperty("jvmTarget") as Property<*> }
                            .map { jvmTarget -> jvmTarget.withGroovyBuilder { getProperty("target") } }
                            .map { value -> value as String }
                    })
                    kotlinApiVersion.convention(kotlinOptionsProvider.flatMap {
                        it.withGroovyBuilder { getProperty("apiVersion") as Property<*> }
                            .map { apiVersion -> apiVersion.withGroovyBuilder { getProperty("version") } }
                            .map { value -> value as String }
                    })
                    kotlinLanguageVersion.convention(kotlinOptionsProvider.flatMap {
                        it.withGroovyBuilder { getProperty("languageVersion") as Property<*> }
                            .map { languageVersion -> languageVersion.withGroovyBuilder { getProperty("version") } }
                            .map { value -> value as String }
                    })
                    kotlinVersion.convention(project.provider {
                        project.extensions.getByName("kotlin")
                            .withGroovyBuilder { getProperty("coreLibrariesVersion") as String }
                    })
                    kotlinStdlibDefaultDependency.convention(
                        project.providers
                            .gradleProperty(GradleProperties.KOTLIN_STDLIB_DEFAULT_DEPENDENCY)
                            .map { it.toBoolean() }
                    )
                }

                project.tasks.withType<JavaCompile> {
                    dependsOn(this@registerTask)
                }
            }
    }

    // TODO: check it
//    private fun verifyJavaPluginDependency(project: Project, ideaDependency: IdeaDependency, plugins: List<Any>) {
//        val hasJavaPluginDependency = plugins.contains("java") || plugins.contains("com.intellij.java")
//        if (!hasJavaPluginDependency && File(ideaDependency.classes, "plugins/java").exists()) {
//            sourcePluginXmlFiles(project).forEach { path ->
//                parsePluginXml(path)?.dependencies?.forEach {
//                    if (it.dependencyId == "com.intellij.modules.java") {
//                        throw BuildException("The project depends on 'com.intellij.modules.java' module but doesn't declare a compile dependency on it.\nPlease delete 'depends' tag from '${path}' or add Java plugin to Gradle dependencies (https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html#java)")
//                    }
//                }
//            }
//        }
//    }
}
