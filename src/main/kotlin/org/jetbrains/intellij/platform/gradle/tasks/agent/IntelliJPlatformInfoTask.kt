// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.KOTLIN_STDLIB_DEFAULT_DEPENDENCY
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.utils.*

@Serializable
internal data class ProjectInfo(
    val name: String,
    val group: String,
    val version: String,
    val module: Boolean,
    val plugin: Map<String, String?>,
    val requestedIde: TargetIde?,
    val java: Map<String, String?>,
    val kotlin: Map<String, String?>,
    val settings: Map<String, Boolean>,
    val dependencies: List<DependencyIdentity>,
    val commands: List<String>,
    val resolvedIde: TargetIde? = null,
    val resolvedPlugins: List<String> = emptyList(),
    val capabilities: List<Capability> = emptyList(),
    val compilerSettings: Map<String, CompilerSettings> = emptyMap(),
)

@Serializable
internal data class DependencyIdentity(val configuration: String, val group: String?, val name: String, val version: String?)

/** Configured options are not evidence of a selected compiler, its defaults, or compatibility with an IDE. */
@Serializable
internal data class CompilerSettings(
    val configured: Map<String, String?>,
    val effective: Map<String, String?>,
    val state: String,
    val reason: String,
)

/**
 * Writes the project context: plugin identity, requested IntelliJ Platform, configured Java/Kotlin settings, declared
 * platform dependencies, and available capabilities. Effective compiler settings remain unresolved; no JDK is provisioned.
 * The IDE is resolved (and possibly downloaded) only with `--resolve`.
 */
@UntrackedTask(because = "Reports describe the current project's environment")
abstract class IntelliJPlatformInfoTask : AgentReportTask() {

    /**
     * Configuration-time project snapshot, serialized so no project state is retained at execution.
     */
    @get:Internal
    abstract val snapshot: Property<String>

    @get:Internal
    @get:Option(option = "resolve", description = "Resolve the selected IDE and platform plugin dependencies (may download artifacts).")
    abstract val resolve: Property<Boolean>

    @get:Internal
    abstract val platformFiles: ConfigurableFileCollection

    @get:Internal
    abstract val pluginFiles: ConfigurableFileCollection

    @TaskAction
    fun report() {
        var info = json.decodeFromString<ProjectInfo>(snapshot.get())
        val unavailable = mutableListOf<Unavailable>()
        var failure: Exception? = null

        if (info.requestedIde == null) {
            unavailable += Unavailable("requestedIde", "unavailable", "No single directly declared IntelliJ Platform type and version are observable without evaluating providers; local and inherited IDEs are not inspected.")
        }
        when {
            !resolve.get() -> unavailable += Unavailable("resolvedIde", "notRequested", "Use --resolve to resolve the IDE and plugin artifacts.")
            else -> try {
                val root = platformFiles.platformPath()
                val product = root.productInfo()
                info = info.copy(
                    resolvedIde = TargetIde(product.productCode, product.version, product.buildNumber, root.safePathString, "resolved"),
                    resolvedPlugins = pluginFiles.files.map { it.toPath().safePathString }.sorted(),
                )
            } catch (e: Exception) {
                failure = e
                unavailable += Unavailable("resolvedIde", "notResolved", "Requested artifact resolution failed.")
            }
        }

        write(
            type = "info",
            status = when {
                failure != null -> ReportStatus.failed
                unavailable.any { it.state == "unavailable" } -> ReportStatus.partial
                else -> ReportStatus.complete
            },
            data = info,
            // Resolver messages may contain repository URLs or credentials, so they are not copied into the report.
            issues = listOfNotNull(failure?.let { Issue("resolution.failed", "error", "Could not resolve the requested IntelliJ Platform.") }),
            unavailable = unavailable,
        )
        failure?.let { throw GradleException("Requested IntelliJ Platform resolution failed; see the JSON report.", it) }
    }

    init {
        description = "Writes a JSON report with the project context, without resolving an IDE by default."
        resolve.convention(false)
    }

    companion object : Registrable {
        override fun register(project: Project) = project.registerAgentReportTask<IntelliJPlatformInfoTask>(Tasks.INTELLIJ_PLATFORM_INFO) {
            val platformConfiguration = project.configurations.getByName(Configurations.INTELLIJ_PLATFORM_DEPENDENCY)
            val pluginConfiguration = project.configurations.getByName(Configurations.INTELLIJ_PLATFORM_PLUGIN)

            snapshot.convention(project.provider { json.encodeToString(project.projectInfo()) })
            platformFiles.from(resolve.map { if (it) platformConfiguration else emptyList<Any>() })
            pluginFiles.from(resolve.map { if (it) pluginConfiguration else emptyList<Any>() })
        }

        private val settingsAllowlist = listOf(
            GradleProperties.AddDefaultIntelliJPlatformDependencies,
            GradleProperties.DownloadSources,
            GradleProperties.IntellijPlatformIdesCacheEnabled,
            GradleProperties.NoSearchableOptionsWarning,
            GradleProperties.ForceBuildSearchableOptions,
            GradleProperties.PaidPluginSearchableOptionsWarning,
            GradleProperties.SelfUpdateCheck,
            GradleProperties.UseDefaultSandboxExclusions,
            GradleProperties.VerifyPluginDefaultRecommendedIdes,
            GradleProperties.UseCacheRedirector,
        )

        private val dependencyConfigurations = listOf(
            Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
            Configurations.INTELLIJ_PLATFORM_LOCAL,
            Configurations.INTELLIJ_PLATFORM_PLUGIN,
            Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL,
            Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE,
            Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS,
            Configurations.INTELLIJ_PLATFORM_BUNDLED_MODULES,
            Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN,
            Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_LOCAL,
            Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_PLUGINS,
            Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_MODULES,
        )

        private fun Project.projectInfo(): ProjectInfo {
            val extension = extensions.findByType<IntelliJPlatformExtension>()
            val pluginConfiguration = extension?.pluginConfiguration
            val settings = settingsAllowlist.associate { it.toString() to providers[it].get() }.toMutableMap()
            extension?.run {
                autoReload.orNull?.let { settings["autoReload"] = it }
                buildSearchableOptions.orNull?.let { settings["buildSearchableOptions"] = it }
                instrumentCode.orNull?.let { settings["instrumentCode"] = it }
                splitMode.orNull?.let { settings["splitMode"] = it }
            }
            findProperty(KOTLIN_STDLIB_DEFAULT_DEPENDENCY)?.let { settings[KOTLIN_STDLIB_DEFAULT_DEPENDENCY] = it.toString().toBoolean() }

            val dependencies = mutableListOf<DependencyIdentity>()
            dependencyConfigurations.forEach { configurationName ->
                // Unlike iterating dependencies, configureEach does not realize the deferred declarations that resolve the platform.
                configurations.findByName(configurationName)?.dependencies?.configureEach {
                    dependencies += DependencyIdentity(configurationName, group, name, version)
                }
            }

            val java = javaInfo()
            val kotlin = kotlinInfo()
            val capabilities = capabilities()
            fun compilerSettings(configured: Map<String, String?>, applied: Boolean = true) = CompilerSettings(
                configured = configured,
                effective = configured.mapValues { null },
                state = if (applied) "notResolved" else "notApplied",
                reason = if (applied) "Only constant configured values are observed; lazy values, compiler defaults, and the effective toolchain remain unresolved to avoid provisioning a JDK."
                else "The Kotlin JVM plugin is not applied.",
            )

            return ProjectInfo(
                name = name,
                group = group.toString(),
                version = version.toString(),
                module = pluginManager.isModule,
                // sinceBuild is omitted as its convention is derived from the resolved platform.
                plugin = mapOf(
                    "id" to pluginConfiguration?.id?.orNull,
                    "name" to pluginConfiguration?.name?.orNull,
                    "version" to pluginConfiguration?.version?.orNull,
                ),
                // Normalized requests resolve release selectors and read artifact options; observe only constant raw identity.
                requestedIde = dependenciesHelper.declaredIntelliJPlatforms().singleOrNull()?.let {
                    val type = configuredValue(it.type) as? IntelliJPlatformType
                    val version = configuredValue(it.version) as? String
                    if (type != null && version != null) TargetIde(type.code, version, provenance = "requested") else null
                },
                java = java,
                kotlin = kotlin,
                settings = settings.toSortedMap(),
                dependencies = dependencies.distinct().sortedWith(compareBy({ it.configuration }, { it.group }, { it.name }, { it.version })),
                commands = capabilities.map { it.task },
                capabilities = capabilities,
                compilerSettings = mapOf(
                    "java" to compilerSettings(java.filterKeys { it !in setOf("gradleJvm", "gradleJvmVendor") }),
                    "kotlin" to compilerSettings(kotlin.filterKeys { it !in setOf("version", "status") }, kotlin["status"] != "notApplied"),
                ),
            )
        }

        private fun Project.javaInfo(): Map<String, String?> {
            val extension = extensions.findByType<JavaPluginExtension>()
            // The public compatibility getters fall back to the toolchain, which would provision a JDK; read declared values only.
            fun declared(getter: String) = runCatching { extension?.javaClass?.getMethod(getter)?.invoke(extension)?.toString() }.getOrNull()

            return mapOf(
                "release" to configuredValue((tasks.findByName(Tasks.External.COMPILE_JAVA) as? JavaCompile)?.options?.release)?.toString(),
                "sourceCompatibility" to declared("getRawSourceCompatibility"),
                "targetCompatibility" to declared("getRawTargetCompatibility"),
                "toolchainLanguageVersion" to configuredValue(extension?.toolchain?.languageVersion)?.toString(),
                "toolchainVendor" to configuredValue(extension?.toolchain?.vendor)?.toString(),
                "gradleJvm" to JavaVersion.current().toString(),
                "gradleJvmVendor" to providers.systemProperty("java.vendor").orNull,
            )
        }

        private fun Project.kotlinInfo(): Map<String, String?> {
            if (!pluginManager.hasPlugin(Plugins.External.KOTLIN)) {
                return mapOf("status" to "notApplied")
            }
            val version = runCatching { extensions.getByName("kotlin").withGroovyBuilder { getProperty("coreLibrariesVersion") as String } }.getOrNull()
            val options = runCatching {
                val compileKotlin = tasks.getByName(Tasks.External.COMPILE_KOTLIN)
                when {
                    Version.parse(version.orEmpty()) >= Version(1, 8, 0) -> compileKotlin.withGroovyBuilder { getProperty("compilerOptions") }
                    else -> compileKotlin.withGroovyBuilder { getProperty("kotlinOptions") }.withGroovyBuilder { getProperty("options") }
                }
            }.getOrNull()

            return mapOf("version" to version) + listOf("jvmTarget" to "target", "apiVersion" to "version", "languageVersion" to "version")
                .associate { (name, valueProperty) ->
                    name to runCatching {
                        when (val value = configuredValue(options?.withGroovyBuilder { getProperty(name) })) {
                            null, is String -> value
                            else -> value.withGroovyBuilder { getProperty(valueProperty) as String }
                        }
                    }.getOrNull()
                }
        }

        internal fun configuredValue(value: Any?, depth: Int = 0): Any? = when {
            value !is Provider<*> -> value
            depth >= 16 -> null
            // Gradle's public Provider getters can evaluate toolchain-backed conventions. Inspect only their constant suppliers.
            value is Property<*> && value.javaClass.name == "org.gradle.api.internal.provider.DefaultProperty" -> runCatching {
                configuredValue(value.javaClass.getMethod("getProvider").invoke(value), depth + 1)
            }.getOrNull()
            value.javaClass.name == "org.gradle.api.internal.provider.Providers\$FixedValueProvider" -> value.orNull
            else -> null
        }
    }
}
