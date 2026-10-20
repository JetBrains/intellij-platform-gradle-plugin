// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.Registrable

@Serializable
internal data class GraphDependencySnapshot(
    val data: GraphDependencies,
    val issues: List<Issue>,
    val unavailable: List<Unavailable>,
    val failed: Boolean,
)

@Serializable
internal data class GraphDependencies(
    val mode: String,
    val configurationFilter: String?,
    val dependencyFilter: String?,
    val configurations: List<GraphConfiguration>,
)

@Serializable
internal data class GraphConfiguration(
    val name: String,
    val roles: List<String>,
    val canBeResolved: Boolean,
    val extendsFrom: List<String>,
    val exclusions: List<GraphExclusion>,
    val dependencies: List<GraphDeclaredDependency>,
    val constraints: List<GraphConstraint>,
    val resolution: GraphResolution?,
)

@Serializable
internal data class GraphExclusion(val group: String?, val module: String?)

@Serializable
internal data class GraphVersionConstraint(val required: String, val preferred: String, val strictly: String, val rejected: List<String>)

@Serializable
internal data class GraphDeclaredDependency(
    val declaredIn: String,
    val kind: String,
    val group: String?,
    val name: String,
    val version: String?,
    val projectPath: String?,
    val targetConfiguration: String?,
    val transitive: Boolean?,
    val exclusions: List<GraphExclusion>,
    val versionConstraint: GraphVersionConstraint?,
)

@Serializable
internal data class GraphConstraint(val declaredIn: String, val group: String, val name: String, val version: String?, val versionConstraint: GraphVersionConstraint)

@Serializable
internal data class GraphResolution(val root: String?, val components: List<GraphComponent>, val edges: List<GraphDependencyEdge>, val failed: Boolean)

@Serializable
internal data class GraphComponent(
    val id: String,
    val kind: String,
    val group: String?,
    val name: String?,
    val version: String?,
    val projectPath: String?,
    val buildPath: String?,
    val selectionReasons: List<GraphSelectionReason>,
    val variants: List<GraphVariant>,
)

@Serializable
internal data class GraphSelectionReason(val cause: String, val description: String)

@Serializable
internal data class GraphVariant(val name: String, val attributes: Map<String, String>, val capabilities: List<String>)

@Serializable
internal data class GraphSelector(
    val displayName: String,
    val group: String? = null,
    val name: String? = null,
    val version: String? = null,
    val projectPath: String? = null,
    val versionConstraint: GraphVersionConstraint? = null,
)

@Serializable
internal data class GraphDependencyEdge(
    val from: String,
    val requested: GraphSelector,
    val selected: String?,
    val selectedVersion: String?,
    val variant: GraphVariant?,
    val constraint: Boolean,
    val unresolved: Boolean,
    val attempted: GraphSelector? = null,
)

/**
 * Writes declared dependencies by default, without realizing deferred declarations or resolving configurations.
 * `--configuration` selects an exact configuration name; `--dependency` is a case-sensitive notation substring.
 * `--resolve` queries ResolutionResult for selected resolvable configurations, not artifact files. Gradle's own
 * deferred declarations and resolution callbacks can perform additional work when resolution is requested.
 * Dependency filtering affects reported declarations and edges, not resolution or conflict selection; edge endpoints
 * and the root are retained. Inherited declarations include their originating configuration in `declaredIn`.
 * A failed requested resolution writes a sanitized report before failing the task.
 */
@UntrackedTask(because = "The graph describes the currently configured dependencies")
abstract class IntelliJPlatformDependencyGraphTask : AgentReportTask() {

    /** Configuration-time snapshot, containing only serialized graph data and sanitized failure observations. */
    @get:Internal
    abstract val snapshot: Property<String>

    /** Exact configuration name to report; an empty value selects all configurations. */
    @get:Internal
    @get:Option(option = "configuration", description = "Report this exact configuration name (all by default).")
    abstract val configurationName: Property<String>

    /** Case-sensitive substring of a dependency's requested or selected notation. */
    @get:Internal
    @get:Option(option = "dependency", description = "Filter dependency notations by this case-sensitive substring.")
    abstract val dependencyFilter: Property<String>

    /** Whether to resolve dependency metadata; declaration-only reporting is the default. */
    @get:Internal
    @get:Option(option = "resolve", description = "Resolve dependency metadata for selected resolvable configurations (may download metadata).")
    abstract val resolve: Property<Boolean>

    /** Writes the graph before surfacing any explicitly requested resolution or configuration-selection failure. */
    @TaskAction
    fun report() {
        val graph = json.decodeFromString<GraphDependencySnapshot>(snapshot.get())
        write(
            "dependencyGraph",
            when {
                graph.failed -> ReportStatus.failed
                graph.unavailable.any { it.state == "unavailable" } -> ReportStatus.partial
                else -> ReportStatus.complete
            },
            graph.data,
            issues = graph.issues,
            unavailable = graph.unavailable,
        )
        if (graph.failed) {
            throw GradleException("The requested dependency graph could not be resolved or selected; see the JSON report.")
        }
    }

    init {
        description = "Writes a JSON dependency graph without resolution by default; --resolve opts into metadata resolution."
        configurationName.convention("")
        dependencyFilter.convention("")
        resolve.convention(false)
    }

    /** Registers the dependency graph report with the shared report conventions. */
    companion object : Registrable {
        override fun register(project: Project) = project.registerAgentReportTask<IntelliJPlatformDependencyGraphTask>(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH) {
            projectPath.convention(project.path)
            val selectedConfiguration = configurationName
            val selectedDependency = dependencyFilter
            val resolveRequested = resolve
            snapshot.convention(project.provider {
                json.encodeToString(project.dependencyGraph(selectedConfiguration.get(), selectedDependency.get(), resolveRequested.get()))
            })
        }

        private fun Project.dependencyGraph(configurationFilter: String, dependencyFilter: String, resolve: Boolean): GraphDependencySnapshot {
            val issues = mutableListOf<Issue>()
            val unavailable = mutableListOf(
                Unavailable("deferredDependencies", "notRequested", "Deferred declarations are not realized by declaration inspection, because they can resolve the IntelliJ Platform."),
                Unavailable("fileDependencies", "notRequested", "File dependency paths and producer outputs are not evaluated; they do not participate in Gradle's component graph."),
                Unavailable("transitiveExclusions", if (resolve) "unavailable" else "notRequested", "Only exclusions on declared configurations and dependencies are exposed; ResolutionResult does not expose transitive metadata exclusion rules."),
            )
            if (!resolve) {
                unavailable += Unavailable("resolution", "notRequested", "Use --resolve to observe selected components, transitive relationships, variants, and unresolved dependencies.")
            }
            val configurations = when {
                configurationFilter.isEmpty() -> configurations.sortedBy { it.name }
                else -> listOfNotNull(configurations.findByName(configurationFilter))
            }
            if (configurations.isEmpty() && configurationFilter.isNotEmpty()) {
                issues += Issue("configuration.notFound", "error", "The requested configuration does not exist.", configurationFilter)
            }
            val graphs = configurations.map { configuration ->
                val declared = mutableListOf<GraphDeclaredDependency>()
                val constraints = mutableListOf<GraphConstraint>()
                // Do not iterate dependencies/allDependencies: that realizes deferred IDE declarations and can download an IDE.
                configuration.hierarchy.sortedBy { it.name }.forEach { origin ->
                    origin.dependencies.configureEach { declared += graphDeclaration(origin.name) }
                    origin.dependencyConstraints.configureEach {
                        constraints += GraphConstraint(origin.name, group, name, version, versionConstraint.graphConstraint())
                    }
                }
                val resolution = when {
                    !resolve -> null
                    !configuration.isCanBeResolved -> {
                        if (configurationFilter.isNotEmpty()) {
                            issues += Issue("configuration.notResolvable", "error", "The requested configuration is declaration-only; select a resolvable classpath instead.", configuration.name)
                            unavailable += Unavailable("resolution.${configuration.name}", "notResolved", "This configuration cannot be resolved.")
                        }
                        null
                    }
                    else -> try {
                        configuration.graphResolution(dependencyFilter).also {
                            if (it.failed) {
                                issues += Issue("resolution.failed", "error", "The requested configuration contains unresolved dependencies (possibly outside the dependency filter).", configuration.name)
                                unavailable += Unavailable("resolution.${configuration.name}", "notResolved", "One or more dependencies could not be resolved.")
                            }
                        }
                    } catch (_: Exception) {
                        // Resolver exception messages can contain repository URLs and credentials. Never serialize them.
                        issues += Issue("resolution.failed", "error", "The requested configuration could not be resolved.", configuration.name)
                        unavailable += Unavailable("resolution.${configuration.name}", "notResolved", "Dependency metadata resolution failed.")
                        GraphResolution(null, emptyList(), emptyList(), true)
                    }
                }
                GraphConfiguration(
                    configuration.name,
                    graphConfigurationRoles(configuration.name),
                    configuration.isCanBeResolved,
                    configuration.extendsFrom.map { it.name }.sorted(),
                    configuration.excludeRules.graphExclusions(),
                    declared.distinct().filter { matches(dependencyFilter, it.group, it.name, it.version, it.projectPath) }
                        .sortedWith(compareBy({ it.kind }, { it.group }, { it.name }, { it.version }, { it.projectPath }, { json.encodeToString(it) })),
                    constraints.distinct().filter { matches(dependencyFilter, it.group, it.name, it.version) }
                        .sortedWith(compareBy({ it.group }, { it.name }, { it.version }, { json.encodeToString(it) })),
                    resolution,
                )
            }
            if (resolve && configurations.any { !it.isCanBeResolved } && configurationFilter.isEmpty()) {
                unavailable += Unavailable("nonResolvableConfigurations", "notRequested", "Declaration-only configurations are reported but not resolved; their resolvable consumers can be selected with --configuration.")
            }
            return GraphDependencySnapshot(
                GraphDependencies(if (resolve) "resolved" else "declared", configurationFilter.ifEmpty { null }, dependencyFilter.ifEmpty { null }, graphs),
                issues.sortedWith(compareBy({ it.setting }, { it.id })),
                unavailable.sortedBy { it.observation },
                issues.any { it.severity == "error" },
            )
        }

        private fun Dependency.graphDeclaration(origin: String) = GraphDeclaredDependency(
            origin,
            when (this) {
                is ProjectDependency -> "project"
                is FileCollectionDependency -> "file"
                is ExternalModuleDependency -> "module"
                else -> "other"
            },
            group,
            name,
            version,
            (this as? ProjectDependency)?.path,
            (this as? ModuleDependency)?.targetConfiguration,
            (this as? ModuleDependency)?.isTransitive,
            (this as? ModuleDependency)?.excludeRules.orEmpty().graphExclusions(),
            (this as? ExternalModuleDependency)?.versionConstraint?.graphConstraint(),
        )

        private fun VersionConstraint.graphConstraint() = GraphVersionConstraint(requiredVersion, preferredVersion, strictVersion, rejectedVersions.sorted())

        private fun Set<ExcludeRule>.graphExclusions() = map { GraphExclusion(it.group, it.module) }.distinct().sortedWith(compareBy({ it.group }, { it.module }))

        private fun Configuration.graphResolution(filter: String): GraphResolution {
            val root = incoming.resolutionResult.rootComponent.get()
            val components = linkedMapOf<String, GraphComponent>()
            val edges = mutableListOf<GraphDependencyEdge>()
            val queue = ArrayDeque<ResolvedComponentResult>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val component = queue.removeFirst()
                val id = component.id.graphId()
                if (id in components) {
                    continue
                }
                val project = component.id as? ProjectComponentIdentifier
                val module = component.moduleVersion
                components[id] = GraphComponent(
                    id,
                    when (component.id) {
                        is ProjectComponentIdentifier -> "project"
                        is ModuleComponentIdentifier -> "module"
                        else -> "other"
                    },
                    module?.group,
                    module?.name,
                    module?.version,
                    project?.projectPath,
                    project?.build?.buildPath,
                    component.selectionReason.descriptions.map { GraphSelectionReason(it.cause.name, it.description) }
                        .distinct().sortedWith(compareBy({ it.cause }, { it.description })),
                    component.variants.map { it.graphVariant() }.distinct().sortedBy { json.encodeToString(it) },
                )
                component.dependencies.forEach { dependency ->
                    when (dependency) {
                        is ResolvedDependencyResult -> {
                            queue.add(dependency.selected)
                            edges += GraphDependencyEdge(
                                id,
                                dependency.requested.graphSelector(),
                                dependency.selected.id.graphId(),
                                dependency.selected.moduleVersion?.version,
                                dependency.resolvedVariant.graphVariant(),
                                dependency.isConstraint,
                                false,
                            )
                        }
                        is UnresolvedDependencyResult -> edges += GraphDependencyEdge(
                            id,
                            dependency.requested.graphSelector(),
                            null,
                            null,
                            null,
                            dependency.isConstraint,
                            true,
                            dependency.attempted.graphSelector(),
                        )
                    }
                }
            }
            val selectedEdges = edges.distinct().filter { edge ->
                val selected = edge.selected?.let { components[it] }
                matches(filter, edge.requested.displayName, edge.requested.projectPath) ||
                    selected?.let { matches(filter, it.group, it.name, it.version, it.projectPath, it.id) } == true
            }.sortedWith(compareBy({ it.from }, { it.requested.displayName }, { it.selected }, { json.encodeToString(it) }))
            val retained = selectedEdges.flatMap { listOfNotNull(it.from, it.selected) }.toSet() + root.id.graphId()
            return GraphResolution(root.id.graphId(), components.values.filter { it.id in retained }.sortedBy { it.id }, selectedEdges, edges.any { it.unresolved })
        }

        private fun ComponentIdentifier.graphId(): String = when (this) {
            is ModuleComponentIdentifier -> "module:$group:$module:$version"
            is ProjectComponentIdentifier -> "project:${build.buildPath}|$projectPath"
            else -> "component:$displayName"
        }

        private fun ComponentSelector.graphSelector() = when (this) {
            is ModuleComponentSelector -> GraphSelector(displayName, group, module, version, versionConstraint = versionConstraint.graphConstraint())
            is ProjectComponentSelector -> GraphSelector(displayName, projectPath = projectPath)
            else -> GraphSelector(displayName)
        }

        private fun ResolvedVariantResult.graphVariant() = GraphVariant(
            displayName,
            attributes.keySet().sortedBy { it.name }.associate { it.name to attributes.getAttribute(it).toString() },
            capabilities.map { "${it.group}:${it.name}:${it.version.orEmpty()}" }.distinct().sorted(),
        )

        private fun matches(filter: String, vararg parts: String?) = filter.isEmpty() || parts.filterNotNull().joinToString(":").contains(filter)
    }
}