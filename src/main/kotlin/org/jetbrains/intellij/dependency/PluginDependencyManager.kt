// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.base.utils.isJar
import com.jetbrains.plugin.structure.base.utils.isZip
import com.jetbrains.plugin.structure.base.utils.simpleName
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.kotlin.dsl.create
import org.gradle.tooling.BuildException
import org.jetbrains.intellij.*
import org.jetbrains.intellij.utils.ArchiveUtils
import java.io.File
import java.nio.file.Paths
import javax.inject.Inject

internal abstract class PluginDependencyManager @Inject constructor(
    gradleHomePath: String,
    private val ideaDependency: IdeaDependency?,
    private val pluginsRepositories: List<PluginsRepository>,
    private val archiveUtils: ArchiveUtils,
    private val context: String?,
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
                info(context, "Looking for builtin '${dependency.id}' in: ${ideaDependency.classes.canonicalPath}")
                ideaDependency.pluginsRegistry.findPlugin(dependency.id)?.let {
                    val builtinPluginVersion = "${ideaDependency.name}-${ideaDependency.buildNumber}" +
                            "-withSources".takeIf { ideaDependency.sources != null }.orEmpty()
                    return PluginDependencyImpl(it.name, dependency.id, builtinPluginVersion, it, true)
                }
            }
            throw BuildException("Cannot find builtin plugin '${dependency.id}' for IDE: ${ideaDependency?.classes?.canonicalPath}", null)
        }
        pluginsRepositories.forEach { repository ->
            repository.resolve(project, dependency, context)?.let {
                val file = it.toPath() // TODO: migrate to Path
                return when {
                    file.isZip() -> zippedPluginDependency(it, dependency)
                    file.isJar() -> externalPluginDependency(it, dependency.channel, true)
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

    private fun zippedPluginDependency(pluginFile: File, dependency: PluginDependencyNotation): PluginDependency? {
        val pluginDir = findSingleDirectory(
            archiveUtils.extract(
                pluginFile,
                File(cacheDirectoryPath, groupId(dependency.channel)).resolve("${dependency.id}-${dependency.version}"),
                context,
            )
        )
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
                ivyPattern("$cacheDirectoryPath/[organisation]/[module]-[revision]$ivyFileSuffix.[ext]") // ivy xml
                ideaDependency?.classes?.let {
                    artifactPattern("$it/plugins/[module]/[artifact](.[ext])") // builtin plugins
                    artifactPattern("$it/[artifact](.[ext])") // plugin sources delivered with IDE
                }
                artifactPattern("$cacheDirectoryPath(/[classifier])/[module]-[revision]/[artifact](.[ext])") // external zip plugins
                if (ideaDependency?.sources != null) {
                    artifactPattern("${ideaDependency.sources.parent}/[artifact]-${ideaDependency.version}(-[classifier]).[ext]")
                }
            }
        }
        if (!plugin.builtin && !plugin.maven) {
            val artifactParent = plugin.artifact.parentFile
            val pluginSource = artifactParent.canonicalPath
            if (artifactParent.parentFile.canonicalPath != cacheDirectoryPath && pluginSources.add(pluginSource)) {
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
        IntelliJIvyDescriptorFileGenerator(identity).apply {
            addConfiguration(DefaultIvyConfiguration("default"))
            addCompileArtifacts(plugin, baseDir, groupId)
            addSourceArtifacts(plugin, baseDir, groupId)
            writeTo(ivyFile)
        }
    }

    private fun IntelliJIvyDescriptorFileGenerator.addCompileArtifacts(
        plugin: PluginDependency,
        baseDir: File,
        groupId: String,
    ) {
        val compileConfiguration = DefaultIvyConfiguration("compile")
        addConfiguration(compileConfiguration)
        plugin.jarFiles.forEach {
            addArtifact(IntellijIvyArtifact.createJarDependency(it, compileConfiguration.name, baseDir, groupId))
        }
        plugin.classesDirectory?.let {
            addArtifact(IntellijIvyArtifact.createDirectoryDependency(it, compileConfiguration.name, baseDir, groupId))
        }
        plugin.metaInfDirectory?.let {
            addArtifact(IntellijIvyArtifact.createDirectoryDependency(it, compileConfiguration.name, baseDir, groupId))
        }
    }

    private fun IntelliJIvyDescriptorFileGenerator.addSourceArtifacts(
        plugin: PluginDependency,
        baseDir: File,
        groupId: String,
    ) {
        val sourcesConfiguration = DefaultIvyConfiguration("sources")
        addConfiguration(sourcesConfiguration)
        if (plugin.sourceJarFiles.isNotEmpty()) {
            plugin.sourceJarFiles.forEach {
                addArtifact(IntellijIvyArtifact.createJarDependency(it, sourcesConfiguration.name, baseDir, groupId))
            }
        } else {
            ideaDependency?.sourceZipFiles?.let {
                IdePluginSourceZipFilesProvider.getSourceZips(ideaDependency, plugin.platformPluginId)?.let {
                    addArtifact(
                        IntellijIvyArtifact.createZipDependency(it, sourcesConfiguration.name, ideaDependency.classes)
                    )
                }
            }
        }
        // see: https://github.com/JetBrains/gradle-intellij-plugin/issues/153
        ideaDependency?.sources?.takeIf { plugin.builtin }?.let {
            val name = if (isDependencyOnPyCharm(ideaDependency)) "pycharmPC" else "ideaIC"
            val artifact = IntellijIvyArtifact(it, name, "jar", "sources", "sources")
            artifact.conf = sourcesConfiguration.name
            addArtifact(artifact)
        }
    }

    private fun externalPluginDependency(artifact: File, channel: String? = null, maven: Boolean = false): PluginDependency? {
        if (!artifact.toPath().isJar() && !artifact.isDirectory) {
            warn(context, "Cannot create plugin from file '$artifact' - only directories or jars are supported")
        }
        return createPlugin(artifact, true, context)?.let {
            val pluginId = it.pluginId ?: return null
            val pluginVersion = it.pluginVersion ?: return null
            return PluginDependencyImpl(pluginId, pluginId, pluginVersion, artifact, false, maven).apply {
                this.channel = channel
                this.sinceBuild = it.sinceBuild?.asStringWithoutProductCode()
                this.untilBuild = it.untilBuild?.asStringWithoutProductCode()
            }
        }
    }
}
