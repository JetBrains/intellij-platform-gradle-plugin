package org.jetbrains.intellij.dependency


import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.IntelliJIvyDescriptorFileGenerator
import org.jetbrains.intellij.Utils

import java.nio.file.Paths

class PluginDependencyManager {
    private final String cacheDirectoryPath
    private final String mavenCacheDirectoryPath
    private final IdeaDependency ideaDependency
    private final List<PluginsRepository> pluginsRepositories

    private Set<String> pluginSources = new HashSet<>()
    private IvyArtifactRepository ivyArtifactRepository

    PluginDependencyManager(@NotNull String gradleHomePath, @Nullable IdeaDependency ideaDependency,
                            @NotNull List<PluginsRepository> pluginsRepositories) {
        this.ideaDependency = ideaDependency
        this.pluginsRepositories = pluginsRepositories
        // todo: a better way to define cache directory
        mavenCacheDirectoryPath = Paths.get(gradleHomePath, 'caches/modules-2/files-2.1').toString()
        cacheDirectoryPath = Paths.get(mavenCacheDirectoryPath, 'com.jetbrains.intellij.idea').toString()
    }

    @NotNull
    PluginDependency resolve(@NotNull Project project, @NotNull PluginDependencyNotation dependency) {
        if (!dependency.version && !dependency.channel) {
            if (Paths.get(dependency.id).absolute) {
                return externalPluginDependency(project, new File(dependency.id), null)
            } else if (ideaDependency) {
                Utils.info(project, "Looking for builtin $dependency.id in $ideaDependency.classes.absolutePath")
                def pluginDirectory = ideaDependency.pluginsRegistry.findPlugin(dependency.id)
                if (pluginDirectory != null) {
                    def builtinPluginVersion = "$ideaDependency.name-$ideaDependency.buildNumber${ideaDependency.sources ? '-withSources' : ''}"
                    return new PluginDependencyImpl(pluginDirectory.name, builtinPluginVersion, pluginDirectory, true)
                }
            }
            throw new BuildException("Cannot find builtin plugin $dependency.id for IDE: $ideaDependency.classes.absolutePath", null)
        }
        for (def repo in this.pluginsRepositories) {
            def pluginFile = repo.resolve(dependency)
            if (pluginFile != null) {
                if (pluginFile != null && Utils.isZipFile(pluginFile)) {
                    return zippedPluginDependency(project, pluginFile, dependency)
                } else if (Utils.isJarFile(pluginFile)) {
                    return externalPluginDependency(project, pluginFile, dependency.channel, true)
                }
                throw new BuildException("Invalid type of downloaded plugin: $pluginFile.name", null)
            }
        }
        throw new BuildException("Cannot resolve plugin $dependency.id version $dependency.version ${dependency.channel != null ? "from channel $dependency.channel" : ""}", null)
    }

    void register(@NotNull Project project, @NotNull PluginDependency plugin, @NotNull DependencySet dependencies) {
        if (plugin.maven && Utils.isJarFile(plugin.artifact)) {
            dependencies.add(plugin.notation.toDependency(project))
            return
        }
        registerRepoIfNeeded(project, plugin)
        generateIvyFile(plugin)
        dependencies.add(project.dependencies.create([
                group: groupId(plugin.channel), name: plugin.id, version: plugin.version, configuration: 'compile'
        ]))
    }

    @NotNull
    private PluginDependency zippedPluginDependency(Project project, File pluginFile, @NotNull PluginDependencyNotation dependency) {
        def pluginDir = findSingleDirectory(Utils.unzip(
                pluginFile, new File(cacheDirectoryPath, groupId(dependency.channel)),
                project, null, null,
                "$dependency.id-$dependency.version"))
        return externalPluginDependency(project, pluginDir, dependency.channel, true)
    }

    private static String groupId(@Nullable String channel) {
        return channel ? "unzipped.${channel}.com.jetbrains.plugins" : 'unzipped.com.jetbrains.plugins'
    }

    private static File findSingleDirectory(@NotNull File dir) {
        def files = dir.listFiles(new FileFilter() {
            @Override
            boolean accept(File pathname) {
                return pathname.isDirectory()
            }
        })
        if (files == null || files.length != 1) {
            throw new BuildException("Single directory expected in $dir", null)
        }
        return files[0]
    }

    private void registerRepoIfNeeded(@NotNull Project project, @NotNull PluginDependency plugin) {
        if (ivyArtifactRepository == null) {
            ivyArtifactRepository = project.repositories.ivy { IvyArtifactRepository repo ->
                repo.ivyPattern("$cacheDirectoryPath/[organisation]/[module]-[revision].[ext]") // ivy xml
                repo.artifactPattern("$ideaDependency.classes/plugins/[module]/[artifact](.[ext])") // builtin plugins
                repo.artifactPattern("$cacheDirectoryPath(/[classifier])/[module]-[revision]/[artifact](.[ext])") // external zip plugins
                if (ideaDependency.sources) {
                    repo.artifactPattern("$ideaDependency.sources.parent/[artifact]-$ideaDependency.version(-[classifier]).[ext]")
                }
            }
        }
        if (!plugin.builtin && !plugin.maven) {
            def artifactParent = plugin.artifact.parentFile
            def pluginSource = artifactParent.absolutePath
            if (artifactParent?.parentFile?.absolutePath != cacheDirectoryPath && pluginSources.add(pluginSource)) {
                ivyArtifactRepository.artifactPattern("$pluginSource/[artifact](.[ext])")  // local plugins
            }
        }
    }

    @NotNull
    private void generateIvyFile(@NotNull PluginDependency plugin) {
        def baseDir = plugin.builtin ? plugin.artifact : plugin.artifact.parentFile
        def pluginFqn = "${plugin.id}-${plugin.version}"
        def groupId = groupId(plugin.channel)
        def ivyFile = new File(new File(cacheDirectoryPath, groupId), "${pluginFqn}.xml")
        if (!ivyFile.exists()) {
            def identity = new DefaultIvyPublicationIdentity(groupId, plugin.id, plugin.version)
            def generator = new IntelliJIvyDescriptorFileGenerator(identity)
            def configuration = new DefaultIvyConfiguration("compile")
            generator.addConfiguration(configuration)
            generator.addConfiguration(new DefaultIvyConfiguration("sources"))
            generator.addConfiguration(new DefaultIvyConfiguration("default"))
            plugin.jarFiles.each {
                generator.addArtifact(Utils.createJarDependency(it, configuration.name, baseDir, groupId))
            }
            if (plugin.classesDirectory) {
                generator.addArtifact(Utils.createDirectoryDependency(plugin.classesDirectory, configuration.name, baseDir, groupId))
            }
            if (plugin.metaInfDirectory) {
                generator.addArtifact(Utils.createDirectoryDependency(plugin.metaInfDirectory, configuration.name, baseDir, groupId))
            }
            if (plugin.builtin && ideaDependency.sources) {
                def artifact = new IntellijIvyArtifact(ideaDependency.sources, "ideaIC", "jar", "sources", "sources")
                artifact.conf = "sources"
                generator.addArtifact(artifact)
            }
            generator.writeTo(ivyFile)
        }
    }

    private static externalPluginDependency(@NotNull Project project, @NotNull File artifact, @Nullable String channel, boolean maven = false) {
        if (!Utils.isJarFile(artifact) && !artifact.isDirectory()) {
            Utils.warn(project, "Cannot create plugin from file ($artifact): only directories or jars are supported")
        }
        def intellijPlugin = Utils.createPlugin(artifact, true, project)
        if (intellijPlugin != null) {
            def pluginDependency = new PluginDependencyImpl(intellijPlugin.pluginId, intellijPlugin.pluginVersion, artifact, false, maven)
            pluginDependency.channel = channel
            //noinspection GroovyAccessibility
            pluginDependency.sinceBuild = intellijPlugin.sinceBuild?.asStringWithoutProductCode()
            //noinspection GroovyAccessibility
            pluginDependency.untilBuild = intellijPlugin.untilBuild?.asStringWithoutProductCode()
            return pluginDependency
        }
        return null
    }
}