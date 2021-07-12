package org.jetbrains.intellij.dependency

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.process.ExecOperations
import org.gradle.tooling.BuildException
import org.jetbrains.intellij.*
import java.io.File
import java.nio.file.Paths
import javax.inject.Inject

@Incubating
open class PluginDependencyManager @Inject constructor(
    gradleHomePath: String,
    private val ideaDependency: IdeaDependency?,
    private val pluginsRepositories: List<PluginsRepository>,
    private val context: String?,
    private val archiveOperations: ArchiveOperations,
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) {

    private val mavenCacheDirectoryPath = Paths.get(gradleHomePath, "caches/modules-2/files-2.1").toString()
    private val cacheDirectoryPath = Paths.get(mavenCacheDirectoryPath, "com.jetbrains.intellij.idea").toString()
    private val pluginSources = mutableSetOf<String>()
    private var ivyArtifactRepository: IvyArtifactRepository? = null

    fun resolve(project: Project, dependency: PluginDependencyNotation): PluginDependency? {
        if (dependency.version.isNullOrEmpty() && dependency.channel.isNullOrEmpty()) {
            if (Paths.get(dependency.id).isAbsolute) {
                return externalPluginDependency(File(dependency.id))
            } else if (ideaDependency != null) {
                info(context, "Looking for builtin '${dependency.id}' in: ${ideaDependency.classes.absolutePath}")
                ideaDependency.pluginsRegistry.findPlugin(dependency.id)?.let {
                    val builtinPluginVersion = "${ideaDependency.name}-${ideaDependency.buildNumber}" +
                        ("-withSources".takeIf { ideaDependency.sources != null } ?: "")
                    return PluginDependencyImpl(it.name, builtinPluginVersion, it, true)
                }
            }
            throw BuildException("Cannot find builtin plugin '${dependency.id}' for IDE: ${ideaDependency?.classes?.absolutePath}", null)
        }
        pluginsRepositories.forEach { repository ->
            repository.resolve(project, dependency, context)?.let {
                return when {
                    it.isZip() -> zippedPluginDependency(it, dependency)
                    it.isJar() -> externalPluginDependency(it, dependency.channel, true)
                    else -> throw BuildException("Invalid type of downloaded plugin: ${it.name}", null)
                }
            }
        }
        throw BuildException(
            "Cannot resolve plugin '${dependency.id}' in version '${dependency.version}'" +
                (" from channel '${dependency.channel}'".takeIf { dependency.channel != null } ?: ""),
            null
        )
    }

    fun register(project: Project, plugin: PluginDependency, dependencies: DependencySet) {
        if (plugin.maven && plugin.artifact.isJar()) {
            dependencies.add(plugin.notation.toDependency(project))
            return
        }
        registerRepositoryIfNeeded(project, plugin)
        generateIvyFile(plugin)
        dependencies.add(project.dependencies.create(
            group = groupId(plugin.channel),
            name = plugin.id,
            version = plugin.version,
            configuration = "compile",
        ))
    }

    private fun zippedPluginDependency(pluginFile: File, dependency: PluginDependencyNotation): PluginDependency? {
        val pluginDir = findSingleDirectory(extractArchive(
            pluginFile,
            File(cacheDirectoryPath, groupId(dependency.channel)).resolve("${dependency.id}-${dependency.version}"),
            archiveOperations,
            execOperations,
            fileSystemOperations,
            context,
        ))
        return externalPluginDependency(pluginDir, dependency.channel, true)
    }

    private fun groupId(channel: String?) = when {
        channel.isNullOrEmpty() -> "unzipped.com.jetbrains.plugins"
        else -> "unzipped.$channel.com.jetbrains.plugins"
    }

    private fun findSingleDirectory(dir: File) =
        dir.listFiles()?.singleOrNull { it.isDirectory } ?: throw BuildException("Single directory expected in: $dir", null)

    private fun registerRepositoryIfNeeded(project: Project, plugin: PluginDependency) {
        if (ivyArtifactRepository == null) {
            ivyArtifactRepository = project.repositories.ivy {
                val ivyFileSuffix = plugin.getFqn().substring("${plugin.id}-${plugin.version}".length)
                it.ivyPattern("$cacheDirectoryPath/[organisation]/[module]-[revision]$ivyFileSuffix.[ext]") // ivy xml
                it.artifactPattern("${ideaDependency?.classes}/plugins/[module]/[artifact](.[ext])") // builtin plugins
                it.artifactPattern("$cacheDirectoryPath(/[classifier])/[module]-[revision]/[artifact](.[ext])") // external zip plugins
                if (ideaDependency?.sources != null) {
                    it.artifactPattern("${ideaDependency.sources.parent}/[artifact]-${ideaDependency.version}(-[classifier]).[ext]")
                }
            }
        }
        if (!plugin.builtin && !plugin.maven) {
            val artifactParent = plugin.artifact.parentFile
            val pluginSource = artifactParent.absolutePath
            if (artifactParent.parentFile.absolutePath != cacheDirectoryPath && pluginSources.add(pluginSource)) {
                ivyArtifactRepository?.artifactPattern("$pluginSource/[artifact](.[ext])")  // local plugins
            }
        }
    }

    private fun generateIvyFile(plugin: PluginDependency) {
        val baseDir = when {
            plugin.builtin -> plugin.artifact
            else -> plugin.artifact.parentFile
        }
        val pluginFqn = plugin.getFqn()
        val groupId = groupId(plugin.channel)
        val ivyFile = File(File(cacheDirectoryPath, groupId), "$pluginFqn.xml").takeUnless { it.exists() } ?: return
        val identity = DefaultIvyPublicationIdentity(groupId, plugin.id, plugin.version)
        val configuration = DefaultIvyConfiguration("compile")

        IntelliJIvyDescriptorFileGenerator(identity).apply {
            addConfiguration(configuration)
            addConfiguration(DefaultIvyConfiguration("sources"))
            addConfiguration(DefaultIvyConfiguration("default"))
            plugin.jarFiles.forEach {
                addArtifact(IntellijIvyArtifact.createJarDependency(it, configuration.name, baseDir, groupId))
            }
            plugin.classesDirectory?.let {
                addArtifact(IntellijIvyArtifact.createDirectoryDependency(it, configuration.name, baseDir, groupId))
            }
            plugin.metaInfDirectory?.let {
                addArtifact(IntellijIvyArtifact.createDirectoryDependency(it, configuration.name, baseDir, groupId))
            }
            ideaDependency?.sources?.takeIf { plugin.builtin }?.let {
                val name = if (isDependencyOnPyCharm(ideaDependency)) "pycharmPC" else "ideaIC"
                val artifact = IntellijIvyArtifact(it, name, "jar", "sources", "sources")
                artifact.conf = "sources"
                addArtifact(artifact)
            }
            writeTo(ivyFile)
        }

    }

    private fun externalPluginDependency(artifact: File, channel: String? = null, maven: Boolean = false): PluginDependency? {
        if (!artifact.isJar() && !artifact.isDirectory) {
            warn(context, "Cannot create plugin from file '$artifact' - only directories or jars are supported")
        }
        return createPlugin(artifact, true, context)?.let {
            val pluginId = it.pluginId ?: return null
            val pluginVersion = it.pluginVersion ?: return null
            return PluginDependencyImpl(pluginId, pluginVersion, artifact, false, maven).apply {
                this.channel = channel
                this.sinceBuild = it.sinceBuild?.asStringWithoutProductCode()
                this.untilBuild = it.untilBuild?.asStringWithoutProductCode()
            }
        }
    }
}
