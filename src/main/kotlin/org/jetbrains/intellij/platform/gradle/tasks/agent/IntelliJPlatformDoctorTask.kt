// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.KotlinMetadataAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.PluginAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.parsePluginXml
import org.jetbrains.intellij.platform.gradle.utils.*
import kotlin.io.path.exists
import kotlin.io.path.readLines

@Serializable
internal data class DoctorReport(val targetIde: TargetIde)

/**
 * Evaluates the plugin project configuration against the resolved IntelliJ Platform and writes the findings as a JSON
 * report with stable identifiers, affected settings, and suggested fixes. Findings never fail the task unless `--fail-on`
 * is requested; the report is always written first.
 */
@UntrackedTask(because = "Diagnostics observe the current project and SDK environment")
abstract class IntelliJPlatformDoctorTask : AgentReportTask(), IntelliJPlatformVersionAware, KotlinMetadataAware, PluginAware {

    @get:Internal
    abstract val sourceCompatibility: Property<String>

    @get:Internal
    abstract val targetCompatibility: Property<String>

    /**
     * Known project build file used to locate effective settings, without claiming source line information.
     */
    @get:Internal
    internal abstract val configurationFile: Property<String>

    /**
     * Whether the IntelliJ Platform cache directory is listed in `.gitignore`; absent when the check is not applicable.
     */
    @get:Internal
    abstract val cacheIgnored: Property<Boolean>

    @get:Internal
    abstract val mutedMessages: ListProperty<String>

    @get:Internal
    @get:Option(option = "fail-on", description = "Fail after writing the report: none (default), warning, or error.")
    abstract val failOn: Property<String>

    @TaskAction
    fun diagnose() {
        val threshold = failOn.get()
        require(threshold in listOf("none", "warning", "error")) { "--fail-on must be none, warning, or error" }

        val product = productInfo
        val target = TargetIde(product.productCode, product.version, product.buildNumber, platformPath.safePathString, "resolved")
        val unavailable = mutableListOf<Unavailable>()
        val isModule = module.get()
        val descriptor = if (isModule) {
            unavailable += Unavailable("pluginXml", "notRequested", "Plugin descriptor checks do not apply to module projects.")
            null
        } else {
            pluginXml.orNull?.asPath?.let { path ->
                val bean = path.parsePluginXml()
                DiagnosticPluginDescriptor(bean.ideaVersion.sinceBuild, bean.ideaVersion.untilBuild, path.safePathString)
            }
        }
        val mutedMessagePatterns = mutedMessages.get()
        val issues = evaluateProjectConfiguration(ProjectConfigurationDiagnosticInput(
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
            cacheIgnored = cacheIgnored.orNull,
            buildFile = configurationFile.orNull,
            verificationCommand = "./gradlew $path",
        )).map { it.issue.copy(suppressed = mutedMessagePatterns.any { pattern -> pattern in it.issue.message }) }
        unavailable += Unavailable("runtimeVendor", "notRequested", "The JetBrains Runtime check requires resolving a runtime; run ${Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION} instead.")

        write("doctor", if (unavailable.any { it.state != "notRequested" }) ReportStatus.partial else ReportStatus.complete, DoctorReport(target), issues, unavailable)

        if (issues.any { !it.suppressed && (threshold == "warning" || threshold == "error" && it.severity == "error") }) {
            throw GradleException("IntelliJ Platform doctor found unsuppressed findings at the requested --fail-on threshold; see the JSON report.")
        }
    }

    init {
        description = "Writes a JSON report with actionable plugin project configuration findings."
        failOn.convention("none")
    }

    companion object : Registrable {
        override fun register(project: Project) = project.registerAgentReportTask<IntelliJPlatformDoctorTask>(Tasks.INTELLIJ_PLATFORM_DOCTOR) {
            val compileJavaTaskProvider = project.tasks.named<JavaCompile>(Tasks.External.COMPILE_JAVA)
            val rootDirectory = project.rootProject.rootDir.toPath()
            val cacheDirectory = project.extensionProvider.flatMap { it.caching.path }

            configurationFile.convention(project.buildFile.toPath().safePathString)
            sourceCompatibility.convention(compileJavaTaskProvider.map { it.options.release.orNull?.toString() ?: it.sourceCompatibility })
            targetCompatibility.convention(compileJavaTaskProvider.map { it.options.release.orNull?.toString() ?: it.targetCompatibility })
            cacheIgnored.convention(project.provider {
                val cache = cacheDirectory.get().asPath
                val gitignore = rootDirectory.resolve(".gitignore")
                when {
                    cache != rootDirectory.resolve(CACHE_DIRECTORY) || !cache.exists() || !gitignore.exists() -> null
                    else -> gitignore.readLines().any { CACHE_DIRECTORY in it }
                }
            })
            mutedMessages.convention(project.providers[GradleProperties.VerifyPluginProjectConfigurationMutedMessages].map { it.splitCommaSeparated() })
        }
    }
}
