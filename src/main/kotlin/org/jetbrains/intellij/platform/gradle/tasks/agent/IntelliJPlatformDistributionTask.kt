// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import java.io.File

/**
 * Reports declared plugin configuration, descriptor metadata, and an observable distribution plan without resolving
 * dependencies, compiling, patching descriptors, or building archives. Raw descriptors have `declared` provenance;
 * an existing patched descriptor has `verified` provenance, but its freshness is not asserted. In particular, the
 * platform-derived `sinceBuild` convention is never queried, even when explicitly overridden.
 *
 * `--inspect` reads the existing configured `buildPlugin` ZIP, and `--archive=path` inspects an existing ZIP at that
 * project-relative or absolute path (implying inspection). Neither option adds a producer-task dependency. Inspection
 * inventories ZIP/JAR entries and plugin, optional, and module descriptors without extracting files. The comparison
 * covers only known planned paths, not bytes, dependency resolution, arbitrary copy specs, or archive freshness.
 * Missing, malformed, unsafe, or oversized requested archives write a failed version-1 report before failing the task.
 * Inspection limits are 100,000 entries per ZIP/JAR and total observed files, 512 XML resources and 16 MiB of XML per
 * container, 1 MiB per descriptor, and 256 MiB of expanded data per nested JAR. This is an inventory, not full archive validation.
 */
@UntrackedTask(because = "Reports observe existing local files without building their producers")
abstract class IntelliJPlatformDistributionTask : AgentReportTask() {

    /** Serialized configuration-time snapshot; execution retains no project or configuration objects. */
    @get:Internal
    abstract val snapshot: Property<String>

    /** Inspect the configured distribution archive only if it already exists. No build task is scheduled. */
    @get:Internal
    @get:Option(option = "inspect", description = "Inspect the existing configured buildPlugin ZIP without building it.")
    abstract val inspect: Property<Boolean>

    /** Existing ZIP to inspect; relative paths are resolved against this project. Implies inspection. */
    @get:Internal
    @get:Option(option = "archive", description = "Inspect this existing distribution ZIP (relative to this project); never build it.")
    abstract val archivePath: Property<String>

    /** Writes the report and then propagates any explicitly requested archive-inspection failure. */
    @TaskAction
    fun report() {
        val configuration = json.decodeFromString<DistributionSnapshot>(snapshot.get())
        val unavailable = mutableListOf(
            Unavailable("configuration.sinceBuild", "notResolved", "The platform-derived convention is not queried. Read an existing patched or archived descriptor for observed compatibility."),
            Unavailable("plan.dependencies", "notResolved", "Only already declared dependencies are observed; deferred declarations, transitive artifacts, exclusions, and filename collisions are not resolved."),
            Unavailable("plan.copySpecs", "unavailable", "The plan describes standard packaging conventions, not arbitrary user copy specs or the complete effective archive layout."),
        )
        val issues = mutableListOf<Issue>()
        var failure: Exception? = null
        val declared = runCatching { readDistributionSources(configuration) }.getOrElse {
            unavailable += Unavailable("declaredDescriptors", "unavailable", "Local descriptor traversal could not be completed within the inspection limits.")
            emptyList()
        }
        declared.filter { it.state != "present" }.forEach {
            unavailable += Unavailable("descriptor:${it.path}", "unavailable", "Declared ${it.kind} descriptor is ${it.state}; module descriptors may be supplied by unresolved module JARs.")
        }
        val patched = configuration.patchedDescriptor?.let { readDistributionDescriptorFile(File(it), "main", "verified") }
        val effective = patched?.takeIf { it.state == "present" }
        if (effective == null) {
            unavailable += Unavailable("effectiveDescriptor", "unavailable", "No readable existing patched plugin.xml is available. Raw descriptors and Gradle configuration are declared, not effective metadata.")
        } else {
            unavailable += Unavailable("effectiveDescriptor.freshness", "unavailable", "The existing patched file was read, but may be stale; the patch task was not run.")
        }
        if (patched?.state == "invalid") issues += Issue("distribution.descriptor.invalid", message = "The existing patched descriptor could not be read as plugin XML.")
        val plan = configuration.plan.observeOverlays()
        plan.nativeVariants.forEach { variant ->
            if (variant.overlaySourcesUnavailable || variant.overlays.any { it.destination == null }) {
                unavailable += Unavailable("nativeVariants.${variant.operatingSystem}.${variant.architecture}.overlays", "unavailable", "Only literal existing file/directory overlay sources are inspected. Lazy providers, file trees, tasks, and absent files are not evaluated or built.")
            }
        }
        if (plan.nativeVariantsEnabled) {
            unavailable += Unavailable("nativeVariants.compatibility", "notResolved", "OS/architecture module injection and version suffixes are planned, not verified; compatibility must start at build 261 and is validated only by the producer task.")
        }
        var archive: DistributionArchive? = null
        if (inspect.get() || archivePath.isPresent) {
            try {
                val path = archivePath.orNull ?: plan.archivePath ?: throw GradleException("No distribution archive is configured for this project.")
                archive = inspectDistributionArchive(File(configuration.projectDirectory).resolve(path), plan)
                if (archive.descriptors.none { it.state == "present" }) {
                    unavailable += Unavailable("archive.descriptors", "unavailable", "No plugin descriptor was observed in this archive.")
                }
                archive.descriptors.filter { it.state != "present" }.forEach {
                    unavailable += Unavailable("archive.descriptor:${it.path}", "unavailable", "The referenced descriptor was not found in the same archive resource container; module descriptors may be in a different JAR.")
                }
                if (archive.comparison.missing.isNotEmpty()) {
                    issues += Issue("distribution.archive.planMismatch", message = "Some observable planned paths are absent from the inspected archive; see archive.comparison. The archive may be stale or customized.")
                }
            } catch (e: Exception) {
                failure = e
                issues += Issue("distribution.archive.failed", "error", "The requested existing distribution archive could not be inspected.")
                unavailable += Unavailable("archive", "unavailable", "Inspection failed. No archive producer, compiler, or dependency resolver was invoked.")
            }
        } else {
            unavailable += Unavailable("archive", "notRequested", "Use --inspect or --archive=path to inspect an existing ZIP without building it.")
        }
        write(
            type = "distribution",
            status = if (failure != null) ReportStatus.failed else if (unavailable.any { it.state != "notRequested" }) ReportStatus.partial else ReportStatus.complete,
            data = DistributionData(
                module = configuration.module,
                configuration = configuration.configuration,
                settings = configuration.settings,
                resourceDirectories = configuration.resourceDirectories,
                descriptorPaths = sortedMapOf("input" to configuration.inputDescriptor, "patched" to configuration.patchedDescriptor),
                declaredDescriptors = declared,
                effectiveDescriptor = effective,
                plan = plan,
                archive = archive,
            ),
            issues = issues.sortedBy { it.id },
            unavailable = unavailable.distinct().sortedWith(compareBy({ it.observation }, { it.state })),
        )
        failure?.let { throw GradleException("Distribution archive inspection failed; see the JSON report.", it) }
    }

    init {
        description = "Writes declared plugin metadata and a distribution plan without resolving or building artifacts."
        inspect.convention(false)
    }

    companion object : Registrable {
        override fun register(project: Project) = project.registerAgentReportTask<IntelliJPlatformDistributionTask>(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION) {
            snapshot.convention(project.provider { json.encodeToString(project.distributionSnapshot()) })
        }
    }
}