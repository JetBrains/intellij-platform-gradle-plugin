package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.api.publish.ivy.internal.publisher.IvyDescriptorFileGenerator
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.Utils

import java.nio.file.Paths

class PluginDependencyManager {
    private static final String INTERNAL_PLUGIN_DEPENDENCY_GROUP = 'internal.com.jetbrains.plugins'

    private final String cacheDirectoryPath
    private final String mavenCacheDirectoryPath
    private final IdeaDependency ideaDependency

    private Set<String> pluginSources = new HashSet<>()
    private IvyArtifactRepository ivyArtifactRepository

    PluginDependencyManager(@NotNull String gradleHomePath, @Nullable IdeaDependency ideaDependency) {
        this.ideaDependency = ideaDependency
        // todo: a better way to define cache directory
        mavenCacheDirectoryPath = Paths.get(gradleHomePath, 'caches/modules-2/files-2.1').toString()
        cacheDirectoryPath = Paths.get(mavenCacheDirectoryPath, 'com.jetbrains.intellij.idea/plugins').toString()
    }

    @NotNull
    PluginDependency resolve(@NotNull Project project, @NotNull String id, @Nullable String version, @Nullable String channel) {
        if (!version && !channel) {
            if (Paths.get(id).absolute) {
                return externalPluginDependency(new File(id), null)
            } else if (ideaDependency) {
                IntelliJPlugin.LOG.info("Looking for builtin $id in $ideaDependency.classes.absolutePath")
                def pluginDirectory = new File(ideaDependency.classes, "plugins/$id").canonicalFile
                if (pluginDirectory.exists() && pluginDirectory.isDirectory()) {
                    def builtinPluginVersion = "$ideaDependency.name-$ideaDependency.version${ideaDependency.sources ? '-withSources' : ''}"
                    return new PluginDependencyImpl(pluginDirectory.name, builtinPluginVersion, pluginDirectory, true)
                }
            }
            throw new BuildException("Cannot find builtin plugin $id for IDE: $ideaDependency.classes.absolutePath", null)
        }
        return resolveRemote(project, id, version, channel)
    }

    void register(@NotNull Project project, @NotNull PluginDependency plugin, @NotNull String configuration) {
        if (plugin.maven && Utils.isJarFile(plugin.artifact)) {
            project.dependencies.add(configuration, pluginDependency(plugin.id, plugin.version, plugin.channel))
            return
        }
        registerRepoIfNeeded(project, plugin)
        generateIvyFile(plugin)
        project.dependencies.add(configuration, [
                group: INTERNAL_PLUGIN_DEPENDENCY_GROUP, name: plugin.id, version: plugin.version, configuration: 'compile'
        ])
    }

    @NotNull
    private PluginDependency resolveRemote(@NotNull Project project, @NotNull String id, @NotNull String version, @Nullable String channel) {
        def dependency = project.dependencies.create(pluginDependency(id, version, channel))
        def configuration = project.configurations.detachedConfiguration(dependency)
        def pluginFile = configuration.singleFile
        if (Utils.isZipFile(pluginFile)) {
            def pluginDir = findSingleDirectory(Utils.unzip(pluginFile, new File(cacheDirectoryPath), project, null, null))
            return externalPluginDependency(pluginDir, channel, true)
        } else if (Utils.isJarFile(pluginFile)) {
            return externalPluginDependency(configuration.singleFile, channel, true)
        }
        throw new BuildException("Invalid type of downloaded plugin: $pluginFile.name", null)
    }

    private static File findSingleDirectory(@NotNull File dir) {
        def files = dir.listFiles(new FileFilter() {
            @Override
            boolean accept(File pathname) {
                return pathname.isDirectory()
            }
        })
        if (files == null || files.length != 1) {
            throw new AssertionError("Single directory expected in $dir")
        }
        return files[0]
    }

    private void registerRepoIfNeeded(@NotNull Project project, @NotNull PluginDependency plugin) {
        if (ivyArtifactRepository == null) {
            ivyArtifactRepository = project.repositories.ivy { IvyArtifactRepository repo ->
                repo.ivyPattern("$cacheDirectoryPath/[module]-[revision].[ext]") // ivy xml
                repo.artifactPattern("$ideaDependency.classes/plugins/[module]/[artifact].[ext]") // builtin plugins
                repo.artifactPattern("$cacheDirectoryPath/[module]-[revision]/[artifact](.[ext])") // external zip plugins
                if (ideaDependency.sources) {
                    repo.artifactPattern("$ideaDependency.sources/[artifact]-[revision](-[classifier]).[ext]")
                }
            }
        }
        if (!plugin.builtin && !plugin.maven) {
            def artifactParent = plugin.artifact.parentFile
            def pluginSource = artifactParent.absolutePath
            if (pluginSource != cacheDirectoryPath && pluginSources.add(pluginSource)) {
                ivyArtifactRepository.artifactPattern("$pluginSource/[artifact].[ext]")  // local plugins
            }
        }
    }

    @NotNull
    private void generateIvyFile(@NotNull PluginDependency plugin) {
        def baseDir = plugin.builtin ? plugin.artifact : plugin.artifact.parentFile
        def pluginFqn = "${plugin.id}-${plugin.version}"
        def ivyFile = new File(cacheDirectoryPath, "${pluginFqn}.xml")
        if (!ivyFile.exists()) {
            def identity = new DefaultIvyPublicationIdentity(INTERNAL_PLUGIN_DEPENDENCY_GROUP, plugin.id, plugin.version)
            def generator = new IvyDescriptorFileGenerator(identity)
            def configuration = new DefaultIvyConfiguration("compile")
            generator.addConfiguration(configuration)
            generator.addConfiguration(new DefaultIvyConfiguration("sources"))
            generator.addConfiguration(new DefaultIvyConfiguration("default"))
            plugin.jarFiles.each { generator.addArtifact(Utils.createJarDependency(it, configuration.name, baseDir)) }
            if (plugin.classesDirectory) {
                generator.addArtifact(Utils.createDirectoryDependency(plugin.classesDirectory, configuration.name, baseDir))
            }
            if (plugin.metaInfDirectory) {
                generator.addArtifact(Utils.createDirectoryDependency(plugin.metaInfDirectory, configuration.name, baseDir))
            }
            if (plugin.builtin && ideaDependency.sources) {
                def artifact = new IntellijIvyArtifact(ideaDependency.sources, "ideaIC", "jar", "sources", "sources")
                artifact.conf = "sources"
                generator.addArtifact(artifact)
            }
            generator.writeTo(ivyFile)
        }
    }

    private static externalPluginDependency(@NotNull File artifact, @Nullable String channel, boolean maven = false) {
        def creationResult = IdePluginManager.createManager().createPlugin(artifact)
        if (creationResult instanceof PluginCreationSuccess) {
            def intellijPlugin = creationResult.plugin
            def pluginDependency = new PluginDependencyImpl(intellijPlugin.pluginId, intellijPlugin.pluginVersion, artifact, false, maven)
            pluginDependency.channel = channel
            //noinspection GroovyAccessibility
            pluginDependency.sinceBuild = intellijPlugin.sinceBuild?.asStringWithoutProductCode()
            //noinspection GroovyAccessibility
            pluginDependency.untilBuild = intellijPlugin.untilBuild?.asStringWithoutProductCode()
            return pluginDependency
        } else if (creationResult instanceof PluginCreationFail) {
            def problems = creationResult.errorsAndWarnings.findAll { it.level == PluginProblem.Level.ERROR }.join(", ")
            IntelliJPlugin.LOG.warn("Cannot create plugin from file ($artifact): $problems")
        } else {
            IntelliJPlugin.LOG.warn("Cannot create plugin from file ($artifact). $creationResult")
        }
        return null
    }

    private static def pluginDependency(@NotNull String id, @NotNull String version, @Nullable String channel) {
        def classifier = channel ? ":$channel" : ""
        return "com.jetbrains.plugins:$id$classifier:$version"
    }
}