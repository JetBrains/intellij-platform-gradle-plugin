// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.tasks.agent.IntelliJPlatformInfoTask.Companion.configuredValue
import org.jetbrains.intellij.platform.gradle.tasks.aware.parsePluginXml
import org.jetbrains.intellij.platform.gradle.utils.PlatformKotlinVersions
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import org.jetbrains.intellij.platform.gradle.utils.toPlatformJavaVersion
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import org.jetbrains.intellij.platform.gradle.variants
import kotlin.io.path.isRegularFile

@Serializable
internal data class MatrixCompatibility(
    val module: Boolean,
    val declaredBounds: MatrixBounds,
    val configuredTargets: List<MatrixCompatibilityTarget>,
    val customTests: List<MatrixTestingDeclaration>,
    val pluginVerifier: MatrixVerifier,
    val projectLanguages: Map<String, String?>,
    val nativeVariants: MatrixNativeVariants,
    val verifiedSuccess: List<String> = emptyList(),
    val evidenceState: String = "notRequested",
)

@Serializable
internal data class MatrixBounds(
    val state: String,
    val descriptor: String? = null,
    val sinceBuild: String? = null,
    val untilBuild: String? = null,
    val provenance: String = "rawDescriptor",
    val effectiveBoundsState: String = "notResolved",
    val requirements: MatrixLanguageRequirements? = null,
)

@Serializable
internal data class MatrixCompatibilityTarget(
    val ide: MatrixIde,
    val purpose: String,
    val command: String?,
    val requirements: MatrixLanguageRequirements,
    val verificationState: String,
    val executionStatus: String = "untested",
)

@Serializable
internal data class MatrixLanguageRequirements(
    val state: String,
    val platformBuild: String? = null,
    val javaRuntime: String? = null,
    val kotlinBundled: String? = null,
    val kotlinApiLevel: String? = null,
    val kotlinCompilerMinimum: String? = null,
    val provenance: String = "platformVersionMappings",
)

@Serializable
internal data class MatrixVerifier(
    val state: String,
    val command: String?,
    val defaultRecommendedSelectionEnabled: Boolean,
    val declarations: List<MatrixDependency>,
    val targets: List<MatrixCompatibilityTarget>,
    val deferredDeclarationsState: String = "notResolved",
    val executionStatus: String = "untested",
)

@Serializable
internal data class MatrixNativeVariants(
    val enabled: Boolean,
    val minimumSinceBuild: String,
    val restrictionState: String,
    val variants: List<MatrixNativeVariant>,
    val executionStatus: String = "untested",
)

@Serializable
internal data class MatrixNativeVariant(
    val os: String,
    val architecture: String,
    val declarationState: String,
    val literalPaths: List<String>,
    val deferredSourceCount: Int,
)

/**
 * Writes declared compatibility bounds, configured IDE targets, language requirements, and native variant restrictions.
 * Raw descriptor bounds are not the patched descriptor's effective bounds. Targets and verification configuration are
 * never evidence of compatibility: this task neither runs verification nor infers success from existing directories.
 */
@UntrackedTask(because = "The matrix observes declarations without building or verifying the plugin")
abstract class IntelliJPlatformCompatibilityMatrixTask : AgentReportTask() {

    /** Serialized configuration-time metadata; no project model is used by the task action. */
    @get:Internal
    abstract val snapshot: Property<String>

    /** Writes the compatibility declarations using the shared versioned JSON envelope. */
    @TaskAction
    fun report() {
        val metadata = json.decodeFromString<MatrixSnapshot<MatrixCompatibility>>(snapshot.get())
        write("compatibilityMatrix", if (metadata.unavailable.isEmpty()) ReportStatus.complete else ReportStatus.partial, metadata.data, metadata.issues, metadata.unavailable)
    }

    init {
        description = "Writes a JSON compatibility matrix without resolving IDEs, building, or running verification."
    }

    /** Registers the metadata-only report without verification or build dependencies. */
    companion object : Registrable {
        override fun register(project: Project) {
            project.registerAgentReportTask<IntelliJPlatformCompatibilityMatrixTask>(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX) {}
            project.gradle.taskGraph.whenReady {
                if (hasTask(project.matrixTaskPath(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX))) {
                    val metadata = json.encodeToString(project.matrixCompatibility())
                    project.tasks.named<IntelliJPlatformCompatibilityMatrixTask>(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX).get().snapshot.set(metadata)
                }
            }
        }
    }
}

private fun Project.matrixCompatibility(): MatrixSnapshot<MatrixCompatibility> {
    val metadata = MatrixMetadata(this)
    val extension = extensions.findByType<IntelliJPlatformExtension>()
    val unavailable = mutableListOf(
        Unavailable("effectiveBounds", "notResolved", "Only raw plugin.xml bounds are read. DSL and patch-task overrides or IDE-derived conventions may change the final bounds."),
        Unavailable("pluginVerifier.deferredDeclarations", "notResolved", "Only constant explicit create declarations and materialized verifier dependencies are observed. Local IDEs, lazy identities, recommended/select targets, and provider lists are not evaluated."),
        Unavailable("verificationEvidence", "notRequested", "No verification evidence is consumed. Configured targets and report-directory existence never establish verified success."),
    )
    val issues = mutableListOf<Issue>()
    val bounds = matrixBounds(metadata.module, issues)
    if (bounds.state != "declared") {
        unavailable += Unavailable("declaredBounds", bounds.state, if (metadata.module) "Module projects need not declare plugin.xml bounds." else "No readable raw plugin.xml bounds are available.")
    }
    val verifierCommand = Tasks.VERIFY_PLUGIN.takeIf { it in tasks.names }?.let { matrixCommand(matrixTaskPath(it)) }
    val verifierDependencies = metadata.dependencies(listOf(Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY, Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_LOCAL_INSTANCE))
    val declaredVerifierIdes = metadata.declaredIdes(Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY, "declaredVerifier").filter { it.state == "configured" }
    val materializedVerifierIdes = verifierDependencies.mapNotNull { dependency ->
        val product = IntelliJPlatformType.entries.firstOrNull { type ->
            listOfNotNull(type.maven, type.installer).any { it.groupId == dependency.group && it.artifactId == dependency.name }
        } ?: return@mapNotNull null
        MatrixIde("configured", product.code, dependency.version, provenance = "materializedVerifierDependency")
    }
    val verifierTargets = (declaredVerifierIdes + materializedVerifierIdes).distinctBy { it.productCode to it.version }.map {
        MatrixCompatibilityTarget(
            it,
            "pluginVerifier",
            verifierCommand,
            matrixLanguageRequirements(it.version),
            "configuredVerification",
        )
    }.sortedWith(compareBy({ it.ide.productCode }, { it.ide.version }))
    val configuredTargets = buildList {
        if (metadata.baseIde.state == "configured") {
            add(MatrixCompatibilityTarget(metadata.baseIde, "development", null, matrixLanguageRequirements(metadata.baseIde.version), "untested"))
        } else {
            unavailable += Unavailable("configuredIde", metadata.baseIde.state, "No inspectable base IntelliJ Platform request is available; local or inherited IDE metadata is not resolved.")
        }
        metadata.testing.forEach {
            add(MatrixCompatibilityTarget(it.ide, it.kind, matrixCommand(it.taskPath), matrixLanguageRequirements(it.ide.version), "configuredTest"))
        }
        addAll(verifierTargets)
    }.sortedWith(compareBy({ it.purpose }, { it.ide.productCode }, { it.ide.version }, { it.command }))
    val enabled = extension?.nativeVariants?.enabled?.get() ?: false
    val native = MatrixNativeVariants(
        enabled,
        Constraints.MINIMAL_NATIVE_VARIANTS_BUILD_NUMBER.major.toString(),
        when {
            !enabled -> "notApplicable"
            else -> "notResolved"
        },
        variants.map { variant ->
            val sources = extension?.nativeVariants?.get(variant)?.from.orEmpty()
            val paths = sources.mapNotNull { matrixLiteralPath(it) }.distinct().sorted()
            MatrixNativeVariant(variant.os, variant.arch, if (sources.isEmpty()) "notConfigured" else "configured", paths, sources.count { matrixLiteralPath(it) == null })
        }.sortedWith(compareBy({ it.os }, { it.architecture })),
    )
    if (enabled) {
        unavailable += Unavailable("nativeVariants.effectiveSinceBuild", "notResolved", "Native variants require effective since-build ${native.minimumSinceBuild} or later. Raw descriptor bounds may be overridden, so the restriction is not validated here.")
    }
    return MatrixSnapshot(
        MatrixCompatibility(
            metadata.module,
            bounds,
            configuredTargets,
            metadata.testing,
            MatrixVerifier(if (verifierCommand == null) "unavailable" else if (verifierTargets.isEmpty() && verifierDependencies.isEmpty()) "notResolved" else "configuredVerification", verifierCommand, providers[GradleProperties.VerifyPluginDefaultRecommendedIdes].get(), verifierDependencies, verifierTargets),
            matrixProjectLanguages(),
            native,
        ),
        issues,
        unavailable.sortedBy { it.observation },
    )
}

private fun Project.matrixBounds(module: Boolean, issues: MutableList<Issue>): MatrixBounds {
    if (module) return MatrixBounds("notRequested")
    val descriptor = (tasks.findByName(Tasks.PATCH_PLUGIN_XML) as? PatchPluginXmlTask)?.inputFile?.orNull?.asFile?.toPath()
        ?: return MatrixBounds("unavailable")
    if (!descriptor.isRegularFile()) return MatrixBounds("unavailable", descriptor.safePathString)
    return runCatching {
        val ideaVersion = descriptor.parsePluginXml().ideaVersion
        val sinceBuild = ideaVersion.sinceBuild?.takeIf { it.isNotBlank() }
        val untilBuild = ideaVersion.untilBuild?.takeIf { it.isNotBlank() }
        MatrixBounds("declared", descriptor.safePathString, sinceBuild, untilBuild, requirements = sinceBuild?.let(::matrixLanguageRequirements))
    }.getOrElse {
        issues += Issue("compatibility.descriptor-unreadable", "warning", "The raw plugin.xml descriptor could not be parsed.", "pluginXml")
        MatrixBounds("unavailable", descriptor.safePathString)
    }
}

internal fun matrixLanguageRequirements(version: String?): MatrixLanguageRequirements {
    val numeric = version?.removeSuffix(".*")?.takeIf { it.matches(Regex("(?:[A-Z]+-)?[0-9]+(?:\\.[0-9]+)*(?:-[A-Za-z0-9.]+)?")) }
        ?: return MatrixLanguageRequirements("unavailable")
    val parsed = numeric.toVersion()
    val build = when {
        parsed.isBuildNumber() -> parsed
        parsed.major >= 2000 && parsed.minor in 1..3 -> Version((parsed.major - 2000) * 10 + parsed.minor)
        else -> return MatrixLanguageRequirements("unavailable")
    }
    val kotlin = PlatformKotlinVersions.entries.firstOrNull { build >= it.key }?.value
    return MatrixLanguageRequirements(
        "mapped",
        build.major.toString(),
        build.toPlatformJavaVersion().majorVersion,
        kotlin?.toString(),
        kotlin?.let { "${it.major}.${it.minor}" },
        "2.0.0".takeIf { build.major >= 251 },
    )
}

private fun Project.matrixProjectLanguages(): Map<String, String?> {
    val java = extensions.findByType<JavaPluginExtension>()
    fun declared(getter: String) = runCatching { java?.javaClass?.getMethod(getter)?.invoke(java)?.toString() }.getOrNull()
    val kotlin = extensions.findByName("kotlin").takeIf { pluginManager.hasPlugin(Plugins.External.KOTLIN) }
    val kotlinVersion = runCatching { kotlin?.withGroovyBuilder { getProperty("coreLibrariesVersion") as String } }.getOrNull()
    val options = runCatching { tasks.findByName(Tasks.External.COMPILE_KOTLIN)?.withGroovyBuilder { getProperty("compilerOptions") } }.getOrNull()
    return mapOf(
        "javaRelease" to configuredValue((tasks.findByName(Tasks.External.COMPILE_JAVA) as? JavaCompile)?.options?.release)?.toString(),
        "javaSourceCompatibility" to declared("getRawSourceCompatibility"),
        "javaTargetCompatibility" to declared("getRawTargetCompatibility"),
        "kotlinVersion" to kotlinVersion,
    ) + listOf("jvmTarget" to "target", "apiVersion" to "version", "languageVersion" to "version").associate { (name, property) ->
        name to runCatching {
            val value = configuredValue(options?.withGroovyBuilder { getProperty(name) })
            when (value) {
                null, is String -> value as String?
                else -> value.withGroovyBuilder { getProperty(property) as String }
            }
        }.getOrNull()
    }
}