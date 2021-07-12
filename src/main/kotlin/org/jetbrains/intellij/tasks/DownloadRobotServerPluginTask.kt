package org.jetbrains.intellij.tasks

import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.intellij.IntelliJPluginConstants.INTELLIJ_DEPENDENCIES
import org.jetbrains.intellij.IntelliJPluginConstants.VERSION_LATEST
import org.jetbrains.intellij.Version
import org.jetbrains.intellij.create
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.extractArchive
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.model.SpacePackagesMavenMetadata
import org.jetbrains.intellij.model.XmlExtractor
import java.net.URI
import java.net.URL
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class DownloadRobotServerPluginTask @Inject constructor(
    objectFactory: ObjectFactory,
    private val archiveOperations: ArchiveOperations,
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) : ConventionTask() {

    companion object {
        private const val METADATA_URL = "$INTELLIJ_DEPENDENCIES/com/intellij/remoterobot/robot-server-plugin/maven-metadata.xml"
        const val OLD_ROBOT_SERVER_DEPENDENCY = "org.jetbrains.test:robot-server-plugin"
        const val NEW_ROBOT_SERVER_DEPENDENCY = "com.intellij.remoterobot:robot-server-plugin"

        fun resolveLatestVersion(): String {
            debug(message = "Resolving latest Robot Server Plugin version")
            val url = URL(METADATA_URL)
            return XmlExtractor<SpacePackagesMavenMetadata>().unmarshal(url.openStream()).versioning?.latest
                ?: throw GradleException("Cannot resolve the latest Robot Server Plugin version")
        }
    }

    @Input
    val version: Property<String> = objectFactory.property(String::class.java)

    @OutputDirectory
    val outputDir: DirectoryProperty = objectFactory.directoryProperty()

    @Transient
    private val dependencyHandler = project.dependencies

    @Transient
    private val repositoryHandler = project.repositories

    @Transient
    private val configurationContainer = project.configurations

    private val context = logCategory()

    /**
     * Resolves Plugin Verifier version.
     * If set to {@link IntelliJPluginConstants#VERSION_LATEST}, there's request to {@link #VERIFIER_METADATA_URL}
     * performed for the latest available verifier version.
     *
     * @return Plugin Verifier version
     */
    private fun resolveVersion() = version.orNull?.takeIf { it != VERSION_LATEST } ?: resolveLatestVersion()

    @TaskAction
    fun downloadPlugin() {
        val resolvedVersion = resolveVersion()
        val (group, name) = getDependency(resolvedVersion).split(':')
        val dependency = dependencyHandler.create(
            group = group,
            name = name,
            version = resolvedVersion,
        )
        val repository = repositoryHandler.maven { it.url = URI.create(INTELLIJ_DEPENDENCIES) }
        val target = outputDir.get().asFile

        try {
            val zipFile = configurationContainer.detachedConfiguration(dependency).singleFile
            extractArchive(zipFile, target, archiveOperations, execOperations, fileSystemOperations, context)
        } finally {
            repositoryHandler.remove(repository)
        }
    }

    private fun getDependency(version: String) = when {
        Version.parse(version) < Version.parse("0.11.0") -> OLD_ROBOT_SERVER_DEPENDENCY
        else -> NEW_ROBOT_SERVER_DEPENDENCY
    }
}
