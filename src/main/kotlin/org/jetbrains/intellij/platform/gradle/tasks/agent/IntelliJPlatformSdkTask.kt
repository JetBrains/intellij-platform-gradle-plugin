// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.intellijPlatformIdeLayoutIndicesCachePath
import org.jetbrains.intellij.platform.gradle.services.IdeLayoutIndexService
import org.jetbrains.intellij.platform.gradle.services.registerClassLoaderScopedBuildService
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.rootProjectPath
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import java.nio.file.Path
import kotlin.io.path.*

/** [owners] is presentation-filtered; classpath owner refs always come from the complete SDK layout catalog. */
@Serializable
internal data class SdkReport(
    val targetIde: TargetIde,
    val root: String,
    val owner: String?,
    val owners: List<LayoutEntry>,
    val compileClasspath: List<CompileArtifact>,
    val sources: List<String>,
)

/**
 * A compilation classpath file: `kind` is `sdk` for files under the IntelliJ Platform root (with the SDK-relative
 * `path` and owning layout entry refs), `project` for files under the root project, or `external` otherwise.
 */
@Serializable
internal data class CompileArtifact(val path: String, val kind: String, val owners: List<String> = emptyList(), val exists: Boolean = true)

/**
 * Writes a file map of the selected IntelliJ Platform: the SDK root, bundled plugins and modules with their archives,
 * the effective main compilation classpath with ownership, and the source archives found in the SDK.
 */
@UntrackedTask(because = "SDK reports describe the currently selected SDK and compiler environment")
abstract class IntelliJPlatformSdkTask : AgentReportTask(), IntelliJPlatformVersionAware {

    /** Filters the presented owner entries, not the classpath or its full-catalog ownership references. */
    @get:Internal
    @get:Option(option = "owner", description = "Show only owners with this exact ID or alias; classpath ownership still uses the complete catalog.")
    abstract val owner: Property<String>

    @get:Internal
    internal abstract val ideLayoutIndexService: Property<IdeLayoutIndexService>

    @get:Internal
    abstract val ideLayoutIndexCacheDirectory: DirectoryProperty

    @get:Internal
    abstract val rootProjectDirectory: DirectoryProperty

    /**
     * The effective main compilation classpath files; absent when their resolution failed.
     */
    @get:Internal
    abstract val compileClasspath: ListProperty<String>

    @TaskAction
    fun report() {
        val root = platformPath.toAbsolutePath().normalize()
        val product = productInfo
        val projectRoot = rootProjectDirectory.asPath.real()
        val owner = owner.orNull
        fun Path.sdkRelative() = takeIf { startsWith(root) }?.let { root.relativize(it).invariantSeparatorsPathString }

        val entries = ideLayoutIndexService.get().resolve(root, ideLayoutIndexCacheDirectory.asPath).layoutEntries()
        val owners = entries
            .map { entry -> entry.copy(archives = entry.archives.mapNotNull { root.resolve(it).normalize().sdkRelative() }.sorted()) }
        val ownersByArchive = owners.flatMap { entry -> entry.archives.map { it to entry.ref } }.groupBy({ it.first }, { it.second })
        val classpath = compileClasspath.orNull?.map { Path(it).toAbsolutePath().normalize() }?.distinct()?.map { file ->
            val relative = file.sdkRelative()
            CompileArtifact(
                path = relative ?: file.safePathString,
                kind = when {
                    relative != null -> "sdk"
                    file.real().startsWith(projectRoot) -> "project"
                    else -> "external"
                },
                owners = ownersByArchive[relative].orEmpty().sorted(),
                exists = file.exists(),
            )
        }
        // Only known SDK locations are inspected; the Gradle caches and the `downloadSources` setting are not evidence of local sources.
        val sources = (listOf(root.resolve("src.zip"), root.resolve("sources.zip")) + root.resolve("lib/src").takeIf { it.isDirectory() }?.listDirectoryEntries().orEmpty())
            .filter { it.isRegularFile() && it.extension in listOf("zip", "jar") }
            .mapNotNull { it.sdkRelative() }.sorted()

        val unavailable = buildList {
            if (classpath == null) add(Unavailable("compileClasspath", "notResolved", "The main compilation classpath could not be resolved."))
            if (sources.isEmpty()) add(Unavailable("sources", "unavailable", "No source archives were found in the selected SDK."))
        }
        write(
            type = "sdk",
            status = if (unavailable.isEmpty()) ReportStatus.complete else ReportStatus.partial,
            data = SdkReport(
                TargetIde(product.productCode, product.version, product.buildNumber, root.safePathString, "resolved"), root.safePathString, owner,
                owners.filter { owner == null || owner == it.id || owner in it.aliases }, classpath.orEmpty(), sources,
            ),
            unavailable = unavailable,
        )
        if (classpath == null) throw GradleException("The main compilation classpath could not be resolved; see the JSON report.")
    }

    private fun Path.real() = runCatching { toRealPath() }.getOrDefault(toAbsolutePath().normalize())

    init {
        description = "Writes a JSON file map of the selected IntelliJ Platform: owners, archives, compilation classpath, and sources."
    }

    companion object : Registrable {
        override fun register(project: Project) = project.registerAgentReportTask<IntelliJPlatformSdkTask>(Tasks.INTELLIJ_PLATFORM_SDK) {
            val compileJavaTaskProvider = project.tasks.named<JavaCompile>(Tasks.External.COMPILE_JAVA)

            rootProjectDirectory.convention(project.rootProject.layout.projectDirectory)
            ideLayoutIndexService.convention(project.gradle.registerClassLoaderScopedBuildService(IdeLayoutIndexService::class))
            ideLayoutIndexCacheDirectory.convention(project.layout.dir(project.provider {
                project.providers.intellijPlatformIdeLayoutIndicesCachePath(project.rootProjectPath).get().toFile()
            }))
            // The raw file set carries no producer task dependencies, so no compilation runs; a resolution failure leaves the property absent.
            compileClasspath.convention(project.provider {
                runCatching { compileJavaTaskProvider.get().classpath.files.map { it.absolutePath }.sorted() }.getOrNull()
            })
        }
    }
}
