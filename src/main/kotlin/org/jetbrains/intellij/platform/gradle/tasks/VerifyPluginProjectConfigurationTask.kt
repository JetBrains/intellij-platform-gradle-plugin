// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.problems.Severity
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.problems.Problems
import org.jetbrains.intellij.platform.gradle.services.PluginXmlService
import org.jetbrains.intellij.platform.gradle.services.pluginXmlService
import org.jetbrains.intellij.platform.gradle.tasks.agent.DiagnosticPluginDescriptor
import org.jetbrains.intellij.platform.gradle.tasks.agent.ProjectConfigurationDiagnosticInput
import org.jetbrains.intellij.platform.gradle.tasks.agent.evaluateProjectConfiguration
import org.jetbrains.intellij.platform.gradle.tasks.aware.*
import org.jetbrains.intellij.platform.gradle.utils.*
import java.io.File
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * Validates the plugin project configuration:
 *
 * - The [PatchPluginXmlTask.sinceBuild] property can't be lower than the target IntelliJ Platform major version.
 * - The effective Java/Kotlin compilation level should align Java versions required by [PatchPluginXmlTask.sinceBuild] and the currently used IntelliJ Platform.
 * - The Kotlin API version should align the version required by [PatchPluginXmlTask.sinceBuild] and the currently used IntelliJ Platform.
 * - The used IntelliJ Platform version must be equal or higher than the minimum supported version `2022.3` (`223`) defined in [MINIMAL_INTELLIJ_PLATFORM_VERSION].
 * - The dependency on the [Kotlin Standard Library](https://jb.gg/intellij-platform-kotlin-stdlib) should be excluded.
 * - The Kotlin Coroutines library [must not be added explicitly](https://jb.gg/intellij-platform-kotlin-coroutines) to the project as it is already provided with the IntelliJ Platform.
 *
 * @see <a href="https://jb.gg/intellij-platform-versions">Build Number Ranges</a>
 */
@CacheableTask
@Suppress("UnstableApiUsage")
abstract class VerifyPluginProjectConfigurationTask : DefaultTask(), IntelliJPlatformVersionAware, KotlinMetadataAware,
    RuntimeAware, PluginAware, ModuleAware, ProblemsAware {

    /**
     * Root project path.
     */
    @get:Internal
    abstract val rootDirectory: Property<File>

    /**
     * IntelliJ Platform cache directory.
     *
     * Default value: `intellijPlatform.caching.path`
     */
    @get:Internal
    abstract val intellijPlatformCache: DirectoryProperty

    /**
     * The `.gitignore` file located in the [rootDirectory], tracked for content change.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val gitignoreFile: RegularFileProperty

    /**
     * Effective Java source compatibility used for verification.
     */
    @get:Internal
    abstract val sourceCompatibility: Property<String>

    /**
     * Effective Java target compatibility used for verification.
     */
    @get:Internal
    abstract val targetCompatibility: Property<String>

    /**
     * List of message patterns to be muted.
     * Each pattern is matched against the message text using a [String.contains] check (case-sensitive).
     */
    @get:Internal
    abstract val mutedMessages: ListProperty<String>

    @get:Internal
    abstract val pluginXmlService: Property<PluginXmlService>

    private val log = Logger(javaClass)

    @TaskAction
    fun verifyPluginConfiguration() {
        val isModule = module.get()
        val product = productInfo
        val descriptor = if (isModule) null else pluginXml.orNull?.let { file ->
            val bean = pluginXmlService.get().resolve(file.asPath)
            DiagnosticPluginDescriptor(bean.ideaVersion.sinceBuild, bean.ideaVersion.untilBuild, file.asPath.safePathString)
        }
        val cacheIgnored = if (isModule) null else gitignoreFile.orNull?.asPath?.let { gitignore ->
            val cache = intellijPlatformCache.asPath
            if (cache.exists() && cache == rootDirectory.get().toPath().resolve(CACHE_DIRECTORY)) {
                gitignore.readLines().any { CACHE_DIRECTORY in it }
            } else null
        }
        val diagnostics = evaluateProjectConfiguration(ProjectConfigurationDiagnosticInput(
            platformVersion = product.version,
            platformBuild = product.buildNumber.toVersion(),
            platformJavaVersion = product.toPlatformJavaVersion(),
            sourceCompatibility = sourceCompatibility.get().toJavaVersion(),
            targetCompatibility = targetCompatibility.get().toJavaVersion(),
            kotlinJvmTarget = kotlinJvmTarget.orNull?.toJavaVersion(),
            kotlinApiVersion = kotlinApiVersion.orNull?.toVersion(),
            kotlinLanguageVersion = kotlinLanguageVersion.orNull?.toVersion(),
            kotlinVersion = kotlinVersion.orNull?.toVersion(),
            kotlinPluginAvailable = kotlinPluginAvailable.get(),
            kotlinStdlibDefaultDependency = kotlinStdlibDefaultDependency.orNull,
            kotlinxCoroutinesLibraryPresent = kotlinxCoroutinesLibraryPresent.get(),
            module = isModule,
            descriptor = descriptor,
            cacheIgnored = cacheIgnored,
        ))
        val mutedMessagePatterns = mutedMessages.getOrElse(emptyList())

        buildList {
            diagnostics.forEach { diagnostic ->
                report(diagnostic.label) {
                    details(diagnostic.details)
                    solution(diagnostic.solution)
                    diagnostic.issue.documentation?.let { documentedAt(it) }
                }
                add(diagnostic.verifierMessage)
            }
            if (runtimeMetadata.get()["java.vendor"] != "JetBrains s.r.o.") {
                val label = "Non-JetBrains Runtime detected"
                val details = "The current Java Runtime is not JetBrains Runtime (JBR). JBR includes IDE-specific patches and optimizations. Using a different runtime may lead to unexpected behaviors, rendering issues, or missing features."
                val solution = "Use IntelliJ Platform with bundled JBR, or configure JVM Toolchain to use JBR explicitly. See: https://jb.gg/intellij-platform-with-jbr"
                report(label) {
                    details(details)
                    solution(solution)
                    documentedAt("https://jb.gg/intellij-platform-with-jbr")
                }
                add("$label $details $solution")
            }
        }
            .filter { message -> mutedMessagePatterns.none { pattern -> message.contains(pattern) } }
            .joinToString("\n") { "- $it" }
            .takeIf { it.isNotEmpty() }
            ?.also { log.warn("The following plugin configuration issues were found:\n$it") }
    }

    private fun report(
        label: String,
        severity: Severity = Severity.WARNING,
        spec: org.gradle.api.problems.ProblemSpec.() -> Unit = {},
    ) {
        problems.reporter.report(Problems.VerifyPluginProjectConfiguration.ConfigurationIssue) {
            contextualLabel(label)
            severity(severity)
            spec()
        }
    }

    init {
        group = Plugin.Groups.VERIFICATION
        description = "Validates the plugin project configuration."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<VerifyPluginProjectConfigurationTask>(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
                log.info("Configuring plugin configuration verification task")

                val compileJavaTaskProvider = project.tasks.named<JavaCompile>(Tasks.External.COMPILE_JAVA)
                val rootDirectoryProvider = project.provider { project.rootProject.rootDir }

                rootDirectory.convention(rootDirectoryProvider)
                intellijPlatformCache.convention(project.extensionProvider.flatMap { it.caching.path } )
                gitignoreFile.convention(project.layout.file(project.provider {
                    project.rootProject.rootDir.resolve(".gitignore").takeIf { it.exists() }
                }))
                pluginXmlService.convention(project.pluginXmlService())
                sourceCompatibility.convention(compileJavaTaskProvider.map { it.options.release.orNull?.toString() ?: it.sourceCompatibility })
                targetCompatibility.convention(compileJavaTaskProvider.map { it.options.release.orNull?.toString() ?: it.targetCompatibility })
                mutedMessages.convention(
                    project.providers[GradleProperties.VerifyPluginProjectConfigurationMutedMessages]
                        .map { it.splitCommaSeparated() }
                )
            }
    }
}
