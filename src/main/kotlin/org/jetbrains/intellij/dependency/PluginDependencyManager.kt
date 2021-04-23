package org.jetbrains.intellij.dependency

import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.tooling.BuildException
import org.jetbrains.intellij.IntelliJIvyDescriptorFileGenerator
import org.jetbrains.intellij.createPlugin
import org.jetbrains.intellij.info
import org.jetbrains.intellij.isJarFile
import org.jetbrains.intellij.isZipFile
import org.jetbrains.intellij.unzip
import org.jetbrains.intellij.warn
import java.io.File
import java.nio.file.Paths

class PluginDependencyManager(
    gradleHomePath: String,
    private val ideaDependency: IdeaDependency?,
    private val pluginsRepositories: List<PluginsRepository>,
) {

    private val mavenCacheDirectoryPath = Paths.get(gradleHomePath, "caches/modules-2/files-2.1").toString()
    private val cacheDirectoryPath = Paths.get(mavenCacheDirectoryPath, "com.jetbrains.intellij.idea").toString()
    private val pluginSources = mutableSetOf<String>()
    private var ivyArtifactRepository: IvyArtifactRepository? = null

    fun resolve(project: Project, dependency: PluginDependencyNotation): PluginDependency? {
        if (dependency.version.isNullOrEmpty() && dependency.channel.isNullOrEmpty()) {
            if (Paths.get(dependency.id).isAbsolute) {
                return externalPluginDependency(project, File(dependency.id), null, false)
            } else if (ideaDependency != null) {
                info(project, "Looking for builtin $dependency.id in $ideaDependency.classes.absolutePath")
                ideaDependency.pluginsRegistry.findPlugin(dependency.id)?.let {
                    val builtinPluginVersion = "${ideaDependency.name}-${ideaDependency.buildNumber}" +
                        ("-withSources".takeIf { ideaDependency.sources != null } ?: "")
                    return PluginDependencyImpl(it.name, builtinPluginVersion, it, true)
                }
            }
            throw BuildException("Cannot find builtin plugin ${dependency.id} for IDE: ${ideaDependency?.classes?.absolutePath}", null)
        }
        pluginsRepositories.forEach { repo ->
            repo.resolve(project, dependency)?.let {
                return when {
                    isZipFile(it) -> zippedPluginDependency(project, it, dependency)
                    isJarFile(it) -> externalPluginDependency(project, it, dependency.channel, true)
                    else -> throw BuildException("Invalid type of downloaded plugin: ${it.name}", null)
                }
            }
        }
        throw BuildException(
            "Cannot resolve plugin ${dependency.id} version ${dependency.version}" +
                (" from channel ${dependency.channel}".takeIf { dependency.channel != null } ?: ""),
            null
        )
    }

    fun register(project: Project, plugin: PluginDependency, dependencies: DependencySet) {
        if (plugin.maven && isJarFile(plugin.artifact)) {
            dependencies.add(plugin.notation.toDependency(project))
            return
        }
        registerRepoIfNeeded(project, plugin)
        generateIvyFile(plugin)
        dependencies.add(project.dependencies.create(mapOf(
            "group" to groupId(plugin.channel),
            "name" to plugin.id,
            "version" to plugin.version,
            "configuration" to "compile",
        )))
    }

    private fun zippedPluginDependency(project: Project, pluginFile: File, dependency: PluginDependencyNotation): PluginDependency? {
        val pluginDir = findSingleDirectory(unzip(
            pluginFile,
            File(cacheDirectoryPath, groupId(dependency.channel)),
            project,
            null,
            null,
            "${dependency.id}-${dependency.version}"
        ))
        return externalPluginDependency(project, pluginDir, dependency.channel, true)
    }

    private fun groupId(channel: String?) = when {
        channel.isNullOrEmpty() -> "unzipped.com.jetbrains.plugins"
        else -> "unzipped.$channel.com.jetbrains.plugins"
    }

    private fun findSingleDirectory(dir: File) =
        dir.listFiles()?.singleOrNull { it.isDirectory } ?: throw BuildException("Single directory expected in $dir", null)

    private fun registerRepoIfNeeded(project: Project, plugin: PluginDependency) {
        if (ivyArtifactRepository == null) {
            ivyArtifactRepository = project.repositories.ivy { repo ->
                repo.ivyPattern("$cacheDirectoryPath/[organisation]/[module]-[revision].[ext]") // ivy xml
                repo.artifactPattern("${ideaDependency?.classes}/plugins/[module]/[artifact](.[ext])") // builtin plugins
                repo.artifactPattern("$cacheDirectoryPath(/[classifier])/[module]-[revision]/[artifact](.[ext])") // external zip plugins
                if (ideaDependency?.sources != null) {
                    repo.artifactPattern("${ideaDependency.sources.parent}/[artifact]-${ideaDependency.version}(-[classifier]).[ext]")
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
        val pluginFqn = "${plugin.id}-${plugin.version}"
        val groupId = groupId(plugin.channel)
        val ivyFile = File(File(cacheDirectoryPath, groupId), "$pluginFqn.xml").takeIf { it.exists() } ?: return
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
                val artifact = IntellijIvyArtifact(it, "ideaIC", "jar", "sources", "sources")
                artifact.conf = "sources"
                addArtifact(artifact)
            }
            writeTo(ivyFile)
        }

    }

    private fun externalPluginDependency(project: Project, artifact: File, channel: String?, maven: Boolean): PluginDependency? {
        if (!isJarFile(artifact) && !artifact.isDirectory) {
            warn(project, "Cannot create plugin from file ($artifact): only directories or jars are supported")
        }
        return createPlugin(artifact, true, project)?.let {
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
