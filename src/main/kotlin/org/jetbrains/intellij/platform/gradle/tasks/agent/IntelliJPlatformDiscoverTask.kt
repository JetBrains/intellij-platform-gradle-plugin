// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.intellijPlatformIdeLayoutIndicesCachePath
import org.jetbrains.intellij.platform.gradle.models.IdeLayoutIndex
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.providers.ProductReleasesFilterParameters
import org.jetbrains.intellij.platform.gradle.services.IdeLayoutIndexService
import org.jetbrains.intellij.platform.gradle.services.registerClassLoaderScopedBuildService
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.tasks.aware.ProductReleasesServiceAware
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.platformPath
import org.jetbrains.intellij.platform.gradle.utils.rootProjectPath
import org.jetbrains.intellij.platform.gradle.utils.safePathString

@Serializable
internal data class DiscoverReport(
    val kind: String,
    val query: String,
    val targetIde: TargetIde? = null,
    val items: List<LayoutEntry> = emptyList(),
    val releases: List<ReleaseEntry> = emptyList(),
)

/**
 * A bundled plugin or module of the IntelliJ Platform. Entries are referenced by their report-local `ref`,
 * so duplicate IDs or aliases never collapse into a single entry.
 */
@Serializable
internal data class LayoutEntry(
    val ref: String,
    val id: String,
    val name: String?,
    val kind: String,
    val aliases: List<String>,
    val dependencies: List<String>,
    val archives: List<String>,
    val originalFile: String?,
)

@Serializable
internal data class ReleaseEntry(
    val productCode: String,
    val version: String,
    val buildNumber: String,
    val channel: String,
    val notation: String,
    val platformVersion: String,
    val platformBuildNumber: String,
)

internal fun IdeLayoutIndex.layoutEntries(): List<LayoutEntry> {
    val sorted = entries.sortedWith(compareBy({ it.id }, { it.isModule }, { it.name.orEmpty() }, { it.originalFile.orEmpty() }, { it.key }))
    val refs = sorted.withIndex().associate { (index, entry) -> entry.key to "entry-$index" }

    return sorted.map { entry ->
        LayoutEntry(
            ref = refs.getValue(entry.key),
            id = entry.id,
            name = entry.name,
            kind = if (entry.isModule) "module" else "plugin",
            aliases = entry.definedModules.sorted(),
            dependencies = entry.dependencies.mapNotNull { refs[it] }.sorted(),
            archives = entry.classpath.sorted(),
            originalFile = entry.originalFile,
        )
    }
}

/**
 * Writes the bundled plugins or modules of the selected IntelliJ Platform, or the available product releases,
 * as a filtered JSON catalog. Release discovery relies on the cached listings only and never resolves an IDE.
 */
@UntrackedTask(because = "Discovery reports describe the current SDK or release listings")
abstract class IntelliJPlatformDiscoverTask : AgentReportTask(), ProductReleasesServiceAware, ProductReleasesFilterParameters {

    @get:Internal
    @get:Option(option = "kind", description = "Discover plugins (default), modules, or releases.")
    abstract val kind: Property<String>

    @get:Internal
    @get:Option(option = "query", description = "Keep only entries whose ID, name, alias, or release notation contains this text (case-insensitive).")
    abstract val query: Property<String>

    @get:Option(option = "since-build", description = "Keep releases at or above this build or version.")
    abstract override val sinceBuild: Property<String>

    @get:Option(option = "until-build", description = "Keep releases at or below this build or version; supports a trailing wildcard.")
    abstract override val untilBuild: Property<String>

    /**
     * The selected IntelliJ Platform files, empty for release discovery.
     */
    @get:Internal
    abstract val platformFiles: ConfigurableFileCollection

    @get:Internal
    internal abstract val ideLayoutIndexService: Property<IdeLayoutIndexService>

    @get:Internal
    abstract val ideLayoutIndexCacheDirectory: DirectoryProperty

    @Option(option = "type", description = "Comma-separated release product codes, such as IU,PS (default all).")
    fun type(value: String) = types.set(value.split(',').map { code ->
        IntelliJPlatformType.entries.lastOrNull { it.code.equals(code.trim(), ignoreCase = true) }
            ?: throw GradleException("Unsupported IntelliJ Platform product code: '$code'")
    })

    @Option(option = "channel", description = "Comma-separated release channels, such as RELEASE,EAP (default all).")
    fun channel(value: String) = channels.set(value.split(',').map { channel ->
        ProductRelease.Channel.entries.firstOrNull { it.name.equals(channel.trim(), ignoreCase = true) }
            ?: throw GradleException("Unsupported product release channel: '$channel'")
    })

    @TaskAction
    fun discover() {
        val kind = kind.get()
        val query = query.get()
        require(kind in listOf("plugins", "modules", "releases")) { "Unsupported discovery kind: '$kind'; use plugins, modules, or releases" }
        var report = DiscoverReport(kind, query)
        val unavailable = mutableListOf<Unavailable>()
        var failure: Exception? = null

        try {
            if (kind == "releases") {
                unavailable += Unavailable("targetIde", "notRequested", "Release discovery does not resolve an IDE.")
                report = report.copy(releases = productReleasesService.get().resolve(this)
                    .filter { query.isEmpty() || listOf(it.notation, it.version.toString(), it.build.toString(), it.channel.name).any { value -> value.contains(query, ignoreCase = true) } }
                    .sortedWith(compareBy<ProductRelease> { it.type.code }.thenByDescending { it.build }.thenBy { it.channel.name })
                    .map { ReleaseEntry(it.type.code, it.version.toString(), it.build.toString(), it.channel.name, it.notation, it.platformVersion.toString(), it.platformBuild.toString()) })
                if (report.releases.isEmpty()) {
                    unavailable += Unavailable("releases", "unavailable", "No releases matched; in offline mode, listings missing from the cache yield no results.")
                }
            } else {
                val root = platformFiles.platformPath()
                val product = root.productInfo()
                report = report.copy(
                    targetIde = TargetIde(product.productCode, product.version, product.buildNumber, root.safePathString, "resolved"),
                    items = ideLayoutIndexService.get().resolve(root, ideLayoutIndexCacheDirectory.asPath).layoutEntries().filter { entry ->
                        entry.kind == kind.removeSuffix("s") && (query.isEmpty() || (listOfNotNull(entry.id, entry.name) + entry.aliases).any { it.contains(query, ignoreCase = true) })
                    },
                )
            }
        } catch (e: Exception) {
            failure = e
            unavailable += Unavailable(if (kind == "releases") "releases" else "targetIde", "notResolved", "Requested discovery failed.")
        }

        write(
            type = "discover",
            status = when {
                failure != null -> ReportStatus.failed
                unavailable.any { it.state == "unavailable" } -> ReportStatus.partial
                else -> ReportStatus.complete
            },
            data = report,
            // Resolver and parser exceptions may contain repository credentials or raw listing content, so they are not copied.
            issues = listOfNotNull(failure?.let { Issue("discovery.failed", "error", "Could not discover the requested IntelliJ Platform entries.") }),
            unavailable = unavailable,
        )
        failure?.let { throw GradleException("IntelliJ Platform discovery failed; see the JSON report.", it) }
    }

    init {
        description = "Writes a filtered JSON catalog of bundled plugins, bundled modules, or IntelliJ Platform releases."
        kind.convention("plugins")
        query.convention("")
        types.convention(IntelliJPlatformType.entries.distinctBy { it.code })
        channels.convention(ProductRelease.Channel.entries)
    }

    companion object : Registrable {
        override fun register(project: Project) = project.registerAgentReportTask<IntelliJPlatformDiscoverTask>(Tasks.INTELLIJ_PLATFORM_DISCOVER) {
            val platformConfiguration = project.configurations.getByName(Configurations.INTELLIJ_PLATFORM_DEPENDENCY)

            platformFiles.from(kind.map { if (it == "releases") emptyList<Any>() else platformConfiguration })
            ideLayoutIndexService.convention(project.gradle.registerClassLoaderScopedBuildService(IdeLayoutIndexService::class))
            ideLayoutIndexCacheDirectory.convention(project.layout.dir(project.provider {
                project.providers.intellijPlatformIdeLayoutIndicesCachePath(project.rootProjectPath).get().toFile()
            }))
        }
    }
}
