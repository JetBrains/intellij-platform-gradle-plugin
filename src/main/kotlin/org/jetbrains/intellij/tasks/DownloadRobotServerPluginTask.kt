package org.jetbrains.intellij.tasks

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
import org.gradle.util.VersionNumber
import org.jetbrains.intellij.create
import org.jetbrains.intellij.extractArchive
import java.net.URI
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class DownloadRobotServerPluginTask @Inject constructor(
    objectFactory: ObjectFactory,
    private val archiveOperations: ArchiveOperations,
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) : ConventionTask() {

    companion object {
        const val ROBOT_SERVER_REPOSITORY = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies"
        const val OLD_ROBOT_SERVER_DEPENDENCY = "org.jetbrains.test:robot-server-plugin"
        const val NEW_ROBOT_SERVER_DEPENDENCY = "com.intellij.remoterobot:robot-server-plugin"
        const val DEFAULT_ROBOT_SERVER_PLUGIN_VERSION = "0.10.0"
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

    @Transient
    @Suppress("LeakingThis")
    private val context = this

    @TaskAction
    fun downloadPlugin() {
        val (group, name) = getDependency().split(':')
        val dependency = dependencyHandler.create(
            group = group,
            name = name,
            version = version.get(),
        )
        val repository = repositoryHandler.maven { it.url = URI.create(ROBOT_SERVER_REPOSITORY) }
        val target = outputDir.get().asFile

        try {
            val zipFile = configurationContainer.detachedConfiguration(dependency).singleFile
            extractArchive(zipFile, target, archiveOperations, execOperations, fileSystemOperations, context)
        } finally {
            repositoryHandler.remove(repository)
        }
    }

    private fun getDependency() = when {
        VersionNumber.parse(version.get()) < VersionNumber.parse("0.11.0") -> OLD_ROBOT_SERVER_DEPENDENCY
        else -> NEW_ROBOT_SERVER_DEPENDENCY
    }
}
