// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.of
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.intellijPlatformIdeLayoutIndicesCachePath
import org.jetbrains.intellij.platform.gradle.intellijPlatformSourcesCachePath
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.providers.IntelliJPlatformSourceArchivesValueSource
import org.jetbrains.intellij.platform.gradle.providers.IntelliJPlatformSourcesRetryValueSource
import org.jetbrains.intellij.platform.gradle.providers.IntelliJPlatformSourcesValueSource
import org.jetbrains.intellij.platform.gradle.providers.SourceArtifactOutcome
import org.jetbrains.intellij.platform.gradle.resolvers.sources.IntelliJPlatformSourceResolver
import org.jetbrains.intellij.platform.gradle.services.IdeLayoutIndexService
import org.jetbrains.intellij.platform.gradle.services.SourceArchiveService
import org.jetbrains.intellij.platform.gradle.services.registerClassLoaderScopedBuildService
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.utils.resolvePlatformPath
import org.jetbrains.intellij.platform.gradle.utils.rootProjectPath
import org.jetbrains.intellij.platform.gradle.utils.settings
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile

@Serializable
internal data class SourcesReport(
    val targetIde: TargetIde,
    val root: String,
    val owner: String?,
    val inventoryFingerprint: String,
    val owners: List<SourceOwner>,
    val binaries: List<SourceBinary>,
    val sources: List<SourceRecord>,
)

/** Writes source discovery and optionally resolves source artifacts using configured Gradle repositories. */
@UntrackedTask(because = "Source discovery observes current SDK archive content and local source availability")
abstract class IntelliJPlatformSourcesTask : AgentReportTask() {
    /** Opts into isolated, non-transitive source artifact resolution. */
    @get:Internal
    @get:Option(option = "download", description = "Resolve source archives using configured repositories and Gradle caches.")
    abstract val download: Property<Boolean>

    /** Expands available source archives into the persistent project source cache. */
    @get:Internal
    @get:Option(option = "extract", description = "Extract available source archives into searchable persistent directories.")
    abstract val extract: Property<Boolean>

    @get:Internal
    internal abstract val sourceCachePath: Property<String>

    @get:Internal
    internal abstract val artifactOutcomes: ListProperty<String>

    @get:Internal
    internal abstract val preparationFailed: Property<Boolean>
    /** Selects binary archives belonging to an exact owner ID or alias before source inspection. */
    @get:Internal
    @get:Option(option = "owner", description = "Inspect only selected binary archives belonging to this exact owner ID or alias.")
    abstract val owner: Property<String>

    @get:Internal
    internal abstract val platformFiles: ConfigurableFileCollection

    @get:Internal
    internal abstract val binaryPaths: ListProperty<String>

    @get:Internal
    internal abstract val ideLayoutIndexService: Property<IdeLayoutIndexService>

    @TaskAction
    fun report() {
        try {
            check(!preparationFailed.get())
            val discovered = Json.decodeFromString<SourcePlan>(discoveryResult.get())
            val resolved = if (download.get()) applySourceOutcomes(discovered, artifactOutcomes.get()) else discovered
            val service = SourceArchiveService()
            val plan = if (!extract.get()) resolved else resolved.copy(sources = resolved.sources.map { source ->
                if (source.availability != "available" || source.archive == null) return@map source
                val notes = mutableListOf<String>()
                runCatching { service.extractRoots(Path(source.archive), Path(sourceCachePath.get())) { notes.add(it) } }.fold(
                    onSuccess = { source.copy(extractedRoot = it.first().safePathString, extractedRoots = it.map { root -> root.safePathString }, extraction = "extracted", diagnostics = source.diagnostics + notes) },
                    onFailure = { source.copy(extraction = "failed", diagnostics = source.diagnostics + "sources.extraction.failed") },
                )
            })
            val input = plan.input
            val unavailable = plan.sources.filter { it.availability != "available" }.map {
                Unavailable(it.id, it.availability, when (it.availability) {
                    "notRequested" -> "Use --download to request source artifacts."
                    "missingOffline" -> "Re-run --download without --offline to populate Gradle's artifact cache using configured repositories."
                    "resolutionError" -> "Check configured repositories, credentials and content restrictions for source artifacts; no repositories are added automatically."
                    else -> "Source candidate unavailable; check configured repositories for source artifacts. This does not establish source coverage or publication status."
                })
            } + plan.binaries.filter { it.inspection != "inspected" }.map {
                Unavailable(it.id, "unavailable", "Binary source metadata could not be completely inspected.")
            } + plan.sources.filter { it.extraction == "failed" }.map {
                Unavailable(it.id, "extractionFailed", "Could not safely extract this source archive; the archive remains available.")
            }
            write("sources", if (plan.binaries.any { it.inspection != "inspected" } || plan.sources.any { it.availability !in listOf("available", "notRequested") || it.extraction == "failed" }) ReportStatus.partial else ReportStatus.complete,
                SourcesReport(TargetIde(input.productCode, input.version, input.buildNumber, input.root, "resolved"),
                    input.root, input.owner, plan.inventoryFingerprint, input.owners, plan.binaries, plan.sources), unavailable = unavailable)
        } catch (_: Exception) {
            write<SourcesReport>("sources", ReportStatus.failed, null,
                issues = listOf(Issue("sources.discovery.failed", "error", "Could not inventory the selected IntelliJ Platform sources.")))
            throw GradleException("IntelliJ Platform source discovery failed; see the JSON report.")
        }
    }

    @get:Internal
    internal abstract val discoveryResult: Property<String>

    init {
        download.convention(false)
        extract.convention(false)
        artifactOutcomes.convention(emptyList())
        preparationFailed.convention(false)
        description = "Writes a source discovery plan; --download opts into source artifact resolution."
    }

    companion object : Registrable {
        override fun register(project: Project) = project.registerAgentReportTask<IntelliJPlatformSourcesTask>(Tasks.INTELLIJ_PLATFORM_SOURCES) {
            sourceCachePath.convention(project.providers.intellijPlatformSourcesCachePath(project.rootProjectPath).map { it.safePathString })
            platformFiles.from(project.configurations.getByName(Configurations.INTELLIJ_PLATFORM_DEPENDENCY))
            val configurations = listOf(Configurations.INTELLIJ_PLATFORM_CLASSPATH, Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS, Configurations.INTELLIJ_PLATFORM_BUNDLED_MODULES)
                .map(project.configurations::getByName)
            binaryPaths.convention(project.provider { configurations.flatMap { it.files }.map { it.absolutePath }.distinct().sorted() })
            ideLayoutIndexService.convention(project.gradle.registerClassLoaderScopedBuildService(IdeLayoutIndexService::class))
            val task = this
            val layoutIndicesCache = project.providers.intellijPlatformIdeLayoutIndicesCachePath(project.rootProjectPath)
            discoveryResult.convention(project.providers.of(IntelliJPlatformSourcesValueSource::class) {
                parameters.root.set(task.platformFiles.elements.map { taskFiles -> taskFiles.single().asFile.toPath().resolvePlatformPath().toString() })
                parameters.binaryPaths.set(task.binaryPaths)
                parameters.owner.set(task.owner)
                parameters.owners.set(project.provider {
                    val root = Path(task.platformFiles.singleFile.toPath().resolvePlatformPath().toString())
                    val cache = layoutIndicesCache.get()
                    Json.encodeToString(task.ideLayoutIndexService.get().resolve(root, cache).layoutEntries().map { entry ->
                        SourceOwner(entry.ref, entry.id, entry.name, entry.kind, entry.aliases, entry.dependencies,
                            entry.archives.mapNotNull { root.resolve(it).normalize().takeIf { path -> path.startsWith(root) }
                                ?.let { root.relativize(it).invariantSeparatorsPathString } }.sorted())
                    })
                })
                parameters.cacheDirectory.set(project.providers.intellijPlatformSourcesCachePath(project.rootProjectPath).map { it.toString() })
                parameters.ruleSchema.set(IntelliJPlatformSourceResolver.RULE_SCHEMA)
                parameters.scannerSchema.set(IntelliJPlatformSourceResolver.SCANNER_SCHEMA)
            })
            var wired = false
            // Task-graph calculation sees CLI options and can register the retry ValueSource as a
            // configuration-cache input. Task actions must not create configurations or access Project.
            dependsOn(project.provider {
                if (task.download.get() && !wired) runCatching {
                    wired = true
                    val plan = Json.decodeFromString<SourcePlan>(task.discoveryResult.get())
                    val candidates = plan.sources.filter { it.availability == "notRequested" }
                    val outcomes = candidates.flatMap { it.candidates }.distinct().associateWith { coordinate ->
                        val configuration = project.configurations.detachedConfiguration(project.dependencies.create(coordinate.notation)).apply {
                            isTransitive = false
                            isCanBeConsumed = false
                        }
                        val artifacts = configuration.incoming.artifactView { isLenient = true }.artifacts
                        val offline = project.gradle.startParameter.isOffline
                        val repositoryManagement = project.settings.dependencyResolutionManagement
                        val effectiveRepositories = when (repositoryManagement.repositoriesMode.get()) {
                            RepositoriesMode.PREFER_PROJECT -> project.repositories.ifEmpty { repositoryManagement.repositories }
                            else -> repositoryManagement.repositories
                        }
                        val noRepositories = effectiveRepositories.isEmpty()
                        val missingState = project.provider {
                            when {
                                offline -> "missingOffline"
                                noRepositories -> "resolutionError"
                                artifacts.failures.all { it.isMissingSourceArtifact() } -> "notFound"
                                else -> "resolutionError"
                            }
                        }
                        artifacts.resolvedArtifacts.zip(missingState) { resolved, missing ->
                            val artifact = resolved.singleOrNull()
                            val selected = artifact?.id?.componentIdentifier as? ModuleComponentIdentifier
                            Json.encodeToString(SourceArtifactOutcome(coordinate, artifact?.file?.toPath()?.safePathString,
                                if (artifact != null) "available" else missing,
                                if (selected != null) coordinate.copy(version = selected.version) else null))
                        }
                    }
                    candidates.filter { it.candidates.isNotEmpty() }.forEach { source ->
                        val ordered = source.candidates.map { outcomes.getValue(it) }
                        task.artifactOutcomes.add(ordered.reduceRight { candidate, fallback ->
                            candidate.map { encoded ->
                                encoded.takeIf { Json.decodeFromString<SourceArtifactOutcome>(it).archive != null }
                            }.orElse(candidate.zip(fallback) { attempted, following ->
                                val previous = Json.decodeFromString<SourceArtifactOutcome>(attempted)
                                val next = Json.decodeFromString<SourceArtifactOutcome>(following)
                                Json.encodeToString(next.copy(
                                    diagnostics = (listOf("sources.${previous.availability}") + previous.diagnostics + next.diagnostics).distinct(),
                                ))
                            })
                        })
                    }
                    val resolvedOutcomes = task.artifactOutcomes.get().map { Json.decodeFromString<SourceArtifactOutcome>(it) }
                    project.providers.of(IntelliJPlatformSourceArchivesValueSource::class) {
                        parameters.archives.set(resolvedOutcomes.mapNotNull { it.archive }.distinct().sorted())
                    }.get()
                    if (resolvedOutcomes.any { it.archive == null }) {
                        project.providers.of(IntelliJPlatformSourcesRetryValueSource::class) {}.get()
                    }
                }.onFailure { task.preparationFailed.set(true) }
                emptyList<Any>()
            })
        }

        private fun Throwable.isMissingSourceArtifact(): Boolean =
            generateSequence(this) { it.cause?.takeUnless { cause -> cause === it } }.take(16).any {
                it.javaClass.simpleName in setOf("ArtifactNotFoundException", "ModuleVersionNotFoundException")
            }

        private fun applySourceOutcomes(plan: SourcePlan, outcomes: List<String>): SourcePlan {
            val results = outcomes.map { Json.decodeFromString<SourceArtifactOutcome>(it) }.associateBy { it.coordinate }
            return plan.copy(sources = plan.sources.map { source ->
                if (source.availability != "notRequested" || source.candidates.isEmpty()) return@map source
                val diagnostics = mutableListOf<String>()
                var selected: SourceRecord? = null
                for ((index, coordinate) in source.candidates.withIndex()) {
                    val outcome = results[coordinate] ?: continue
                    val archive = outcome.archive
                    val state = if (archive != null && !Path(archive).isRegularFile()) "resolutionError" else outcome.availability
                    diagnostics += outcome.diagnostics
                    if (archive != null && state == "available") {
                        selected = source.copy(selectedCoordinate = outcome.selectedCoordinate ?: coordinate, archive = archive, availability = "available",
                            provenance = source.candidateProvenance.getOrElse(index) { if (index == 0) "exactCoordinate" else "compatibleFallback" },
                            diagnostics = source.diagnostics + diagnostics)
                        break
                    }
                    diagnostics += "sources.$state"
                }
                selected ?: source.copy(availability = when {
                    diagnostics.isEmpty() -> "resolutionError"
                    diagnostics.any { it == "sources.missingOffline" } -> "missingOffline"
                    diagnostics.any { it == "sources.resolutionError" } -> "resolutionError"
                    else -> "notFound"
                }, diagnostics = source.diagnostics + diagnostics.distinct())
            })
        }
    }
}
