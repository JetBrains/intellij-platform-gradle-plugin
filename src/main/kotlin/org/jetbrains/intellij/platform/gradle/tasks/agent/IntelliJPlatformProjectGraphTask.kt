// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.utils.safePathString

@Serializable
internal data class GraphProjectSnapshot(val data: GraphProjects, val unavailable: List<Unavailable>)

@Serializable
internal data class GraphProjects(
    val projects: List<GraphProject>,
    val edges: List<GraphProjectEdge>,
    val includedBuilds: List<GraphBuild>,
    val buildLogic: List<GraphBuild>,
)

@Serializable
internal data class GraphProject(
    val path: String,
    val name: String,
    val projectDirectory: String,
    val buildFile: String,
    val plugins: List<String>,
    val sourceSets: List<GraphSourceSet>,
    val roles: List<String>,
)

@Serializable
internal data class GraphSourceSet(val name: String, val role: String, val sourceRoots: List<String>, val resourceRoots: List<String>)

@Serializable
internal data class GraphProjectEdge(
    val from: String,
    val to: String,
    val configuration: String,
    val kind: String,
    val roles: List<String>,
    val targetConfiguration: String?,
)

@Serializable
internal data class GraphBuild(val name: String, val projectDirectory: String, val kind: String)

/**
 * Writes the current build's project structure without resolving dependencies or running producer tasks.
 * Roles are inferred from applied distribution/library plugins, declared project edges, and test source-set names.
 * Included builds are identified, but their project models and plugin implementation origins are not traversed.
 */
@UntrackedTask(because = "The graph describes the currently configured build")
abstract class IntelliJPlatformProjectGraphTask : AgentReportTask() {

    /** Configuration-time snapshot; execution never retains or accesses a project model. */
    @get:Internal
    abstract val snapshot: Property<String>

    /** Writes the serialized project graph in the common report envelope. */
    @TaskAction
    fun report() {
        val graph = json.decodeFromString<GraphProjectSnapshot>(snapshot.get())
        write(
            "projectGraph",
            if (graph.unavailable.any { it.state == "unavailable" }) ReportStatus.partial else ReportStatus.complete,
            graph.data,
            unavailable = graph.unavailable,
        )
    }

    init {
        description = "Writes a JSON graph of projects, source roots, and declared project dependencies without resolution."
    }

    /** Registers the project graph report with the shared report conventions. */
    companion object : Registrable {
        override fun register(project: Project) = project.registerAgentReportTask<IntelliJPlatformProjectGraphTask>(Tasks.INTELLIJ_PLATFORM_PROJECT_GRAPH) {
            projectPath.convention(project.path)
            snapshot.convention(project.provider { json.encodeToString(project.projectGraph()) })
        }

        private fun Project.projectGraph(): GraphProjectSnapshot {
            val unavailable = mutableListOf(
                Unavailable("deferredProjectDependencies", "notRequested", "Deferred declarations are not realized, because doing so can resolve the IntelliJ Platform."),
                Unavailable("includedBuildProjects", "unavailable", "Gradle exposes included-build identities, not their project models, through this project model."),
                Unavailable("pluginOrigins", "unavailable", "Applied plugin classes are observable; plugin IDs and implementation build origins are not generally exposed."),
            )
            val edges = mutableListOf<GraphProjectEdge>()
            val projects = rootProject.allprojects.sortedBy { it.path }.map { candidate ->
                candidate.configurations.sortedBy { it.name }.forEach { configuration ->
                    // Iteration would realize deferred IntelliJ Platform declarations, even in a declaration-only report.
                    configuration.dependencies.configureEach {
                        if (this is ProjectDependency) {
                            edges += GraphProjectEdge(
                                candidate.path,
                                path,
                                configuration.name,
                                when (configuration.name) {
                                    Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE -> "pluginModule"
                                    Configurations.INTELLIJ_PLATFORM_PLUGIN_COMPOSED_MODULE -> "pluginComposedModule"
                                    else -> configuration.name
                                },
                                graphConfigurationRoles(configuration.name),
                                targetConfiguration,
                            )
                        }
                    }
                }
                val sourceSetModel = candidate.extensions.findByType<SourceSetContainer>()
                val sourceSets = sourceSetModel?.sortedBy { it.name }?.map { sourceSet ->
                    val role = if ("test" in sourceSet.name.lowercase()) "test" else if (sourceSet.name == "main") "production" else "custom"
                    runCatching {
                        GraphSourceSet(
                            sourceSet.name,
                            role,
                            sourceSet.allSource.srcDirs.map { it.toPath().safePathString }.distinct().sorted(),
                            sourceSet.resources.srcDirs.map { it.toPath().safePathString }.distinct().sorted(),
                        )
                    }.getOrElse {
                        unavailable += Unavailable("projects.${candidate.path}.sourceSets.${sourceSet.name}.sourceRoots", "unavailable", "Source roots could not be observed without evaluating a failing provider.")
                        GraphSourceSet(sourceSet.name, role, emptyList(), emptyList())
                    }
                }.orEmpty()
                if (sourceSetModel == null) {
                    unavailable += Unavailable("projects.${candidate.path}.sourceSets", "unavailable", "No JVM source-set model is exposed by this project.")
                }
                GraphProject(
                    candidate.path,
                    candidate.name,
                    candidate.projectDir.toPath().safePathString,
                    candidate.buildFile.toPath().safePathString,
                    candidate.plugins.map { it.javaClass.name.removeSuffix("_Decorated") }.distinct().sorted(),
                    sourceSets,
                    buildList {
                        if (candidate.pluginManager.hasPlugin(Plugin.ID)) {
                            add("distribution")
                        } else if (candidate.pluginManager.hasPlugin(Plugins.MODULE) || candidate.pluginManager.hasPlugin("java-library")) {
                            add("shared")
                        }
                        if (sourceSets.any { it.role == "test" }) {
                            add("test")
                        }
                    }.sorted(),
                )
            }
            val orderedEdges = edges.distinct().sortedWith(compareBy({ it.from }, { it.to }, { it.configuration }, { it.targetConfiguration }))
            val shared = orderedEdges.filter { it.kind in listOf("implementation", "api", "pluginModule", "pluginComposedModule") }.map { it.to }.toSet()
            val test = orderedEdges.filter { "test" in it.roles }.map { it.to }.toSet()
            val included = gradle.includedBuilds.map { GraphBuild(it.name, it.projectDir.toPath().safePathString, "included") }
                .sortedWith(compareBy({ it.name }, { it.projectDirectory }))
            val buildLogic = buildList {
                val buildSrc = rootProject.projectDir.resolve("buildSrc")
                if (buildSrc.isDirectory) {
                    add(GraphBuild("buildSrc", buildSrc.toPath().safePathString, "buildSrc"))
                }
                addAll(included.map { it.copy(kind = "includedBuildCandidate") })
            }.sortedWith(compareBy({ it.name }, { it.projectDirectory }))
            unavailable += Unavailable("externalBuildLogic", "unavailable", "buildSrc and included builds are candidates only; settings plugin builds and external convention-plugin origins are not generally observable.")
            return GraphProjectSnapshot(
                GraphProjects(
                    projects.map { node ->
                        node.copy(roles = (node.roles + listOfNotNull("shared".takeIf { node.path in shared }, "test".takeIf { node.path in test })).distinct().sorted())
                    },
                    orderedEdges,
                    included,
                    buildLogic,
                ),
                unavailable.sortedBy { it.observation },
            )
        }
    }
}