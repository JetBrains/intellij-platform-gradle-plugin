// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.base.utils.*
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.provider.Provider
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.kotlin.dsl.create
import org.gradle.tooling.BuildException
import org.jetbrains.intellij.IntelliJIvyDescriptorFileGenerator
import org.jetbrains.intellij.createPlugin
import org.jetbrains.intellij.info
import org.jetbrains.intellij.utils.ArchiveUtils
import org.jetbrains.intellij.warn
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.name

abstract class PluginDependencyManager @Inject constructor(
    gradleHomePath: String,
    private val ideaDependencyProvider: Provider<IdeaDependency>,
    private val pluginsRepositories: List<PluginsRepository>,
    private val archiveUtils: ArchiveUtils,
    private val context: String?,
) {

    private val mavenCacheDirectoryPath = Paths.get(gradleHomePath, "caches/modules-2/files-2.1").toString()
    private val cacheDirectoryPath = Paths.get(mavenCacheDirectoryPath, "com.jetbrains.intellij.idea").toString()
    private val pluginSources = mutableSetOf<String>()
    private var ivyArtifactRepository: IvyArtifactRepository? = null

    fun resolve(project: Project, dependency: PluginDependencyNotation): PluginDependency? {
        val ideaDependency = ideaDependencyProvider.get() // TODO fix

        if (dependency.version.isNullOrEmpty() && dependency.channel.isNullOrEmpty()) {
            if (Paths.get(dependency.id).isAbsolute) {
                return externalPluginDependency(Path.of(dependency.id))
            } else {
                info(context, "Looking for builtin '${dependency.id}' in: ${ideaDependency.classes.canonicalPath}")
                ideaDependency.pluginsRegistry.findPlugin(dependency.id)?.let {
                    val builtinPluginVersion = "${ideaDependency.name}-${ideaDependency.buildNumber}" +
                            "-withSources".takeIf { ideaDependency.sources != null }.orEmpty()
                    return PluginDependencyImpl(it.name, dependency.id, builtinPluginVersion, it.toFile(), true)
                }
            }
            throw BuildException("Cannot find builtin plugin '${dependency.id}' for IDE: ${ideaDependency.classes.canonicalPath}", null)
        }
        pluginsRepositories.forEach { repository ->
            repository.resolve(project, dependency, context)?.let {
                val file = it.toPath() // TODO: migrate to Path
                return when {
                    file.isZip() -> zippedPluginDependency(file, dependency)
                    file.isJar() -> externalPluginDependency(file, dependency.channel, true)
                    else -> throw BuildException("Invalid type of downloaded plugin: ${file.simpleName}", null)
                }
            }
        }
        throw BuildException(
            "Cannot resolve plugin '${dependency.id}' in version '${dependency.version}'" +
                    " from channel '${dependency.channel}'".takeIf { dependency.channel != null }.orEmpty(),
            null
        )
    }

    fun register(project: Project, plugin: PluginDependency, dependencies: DependencySet) {
        if (plugin.maven && plugin.artifact.toPath().isJar()) {
            dependencies.add(plugin.notation.toDependency(project))
            return
        }
        registerRepositoryIfNeeded(project, plugin)
        generateIvyFile(plugin)
        dependencies.add(
            project.dependencies.create(
                group = groupId(plugin.channel),
                name = plugin.id,
                version = plugin.version,
                configuration = "compile",
            )
        )
    }

    private fun zippedPluginDependency(pluginFile: Path, dependency: PluginDependencyNotation): PluginDependency? {
        val pluginDir = findSingleDirectory(
            archiveUtils.extract(
                pluginFile,
                File(cacheDirectoryPath, groupId(dependency.channel)).resolve("${dependency.id}-${dependency.version}").toPath(), // FIXME
                context,
            )
        )
        return externalPluginDependency(pluginDir, dependency.channel, true)
    }

    private fun groupId(channel: String?) = when {
        channel.isNullOrEmpty() -> "unzipped.com.jetbrains.plugins"
        else -> "unzipped.$channel.com.jetbrains.plugins"
    }

    private fun findSingleDirectory(dir: Path) =
        dir.listFiles().singleOrNull { it.isDirectory() } ?: throw BuildException("Single directory expected in: $dir", null)

    private fun registerRepositoryIfNeeded(project: Project, plugin: PluginDependency) {
        val ideaDependency = ideaDependencyProvider.get() // TODO fix

        if (ivyArtifactRepository == null) {
            ivyArtifactRepository = project.repositories.ivy {
                val ivyFileSuffix = plugin.getFqn().substring("${plugin.id}-${plugin.version}".length)
                ivyPattern("$cacheDirectoryPath/[organisation]/[module]-[revision]$ivyFileSuffix.[ext]") // ivy xml
                ideaDependency.classes.let {
                    artifactPattern("$it/plugins/[module]/[artifact](.[ext])") // builtin plugins
                    artifactPattern("$it/[artifact](.[ext])") // plugin sources delivered with IDE
                }
                artifactPattern("$cacheDirectoryPath(/[classifier])/[module]-[revision]/[artifact](.[ext])") // external zip plugins
                if (ideaDependency.sources != null) {
                    artifactPattern("${ideaDependency.sources.parent}/[artifact]-${ideaDependency.version}(-[classifier]).[ext]")
                }
            }
        }
        if (!plugin.builtin && !plugin.maven) {
            val artifactParent = plugin.artifact.toPath().parent
            if (artifactParent.parent != Path.of(cacheDirectoryPath) && pluginSources.add(artifactParent.absolutePathString())) {
                ivyArtifactRepository?.artifactPattern("$artifactParent/[artifact](.[ext])")  // local plugins
            }
        }
    }

    private fun generateIvyFile(plugin: PluginDependency) {
        val ideaDependency = ideaDependencyProvider.get() // TODO fix

        val baseDir = when {
            plugin.builtin -> plugin.artifact.toPath()
            else -> plugin.artifact.toPath().parent
        }
        val pluginFqn = plugin.getFqn()
        val groupId = groupId(plugin.channel)
        val ivyFile = Path.of(cacheDirectoryPath).resolve(groupId).resolve("$pluginFqn.xml").takeUnless { it.exists() } ?: return
        val identity = DefaultIvyPublicationIdentity(groupId, plugin.id, plugin.version)
        IntelliJIvyDescriptorFileGenerator(identity)
            .addConfiguration(DefaultIvyConfiguration("default"))
            .addCompileArtifacts(plugin, baseDir, groupId)
            .addSourceArtifacts(ideaDependency, plugin, baseDir, groupId)
            .writeTo(ivyFile)
    }

    private fun externalPluginDependency(artifact: Path, channel: String? = null, maven: Boolean = false): PluginDependency? {
        if (!artifact.isJar() && !artifact.isDirectory()) {
            warn(context, "Cannot create plugin from file '$artifact' - only directories or jars are supported")
        }
        return createPlugin(artifact, true, context)?.let {
            val pluginId = it.pluginId ?: return null
            val pluginVersion = it.pluginVersion ?: return null
            return PluginDependencyImpl(pluginId, pluginId, pluginVersion, artifact.toFile(), false, maven).apply {
                this.channel = channel
                this.sinceBuild = it.sinceBuild?.asStringWithoutProductCode()
                this.untilBuild = it.untilBuild?.asStringWithoutProductCode()
            }
        }
    }
}
