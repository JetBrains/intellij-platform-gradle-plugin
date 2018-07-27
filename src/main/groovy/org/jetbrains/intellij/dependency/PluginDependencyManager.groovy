package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import com.jetbrains.plugin.structure.intellij.utils.StringUtil
import org.gradle.api.Project
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.api.publish.ivy.internal.publisher.IvyDescriptorFileGenerator
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.Utils
import org.jetbrains.intellij.pluginRepository.PluginRepositoryInstance

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

class PluginDependencyManager {
    private final String cacheDirectoryPath
    private final String repositoryHost
    private final IdeaDependency ideaDependency

    private boolean repoRegistered
    private Set<String> pluginSources = new HashSet<>()

    PluginDependencyManager(
            @NotNull String gradleHomePath, @Nullable IdeaDependency ideaDependency, @NotNull String pluginRepoUrl) {
        this.repositoryHost = pluginRepoUrl
        this.ideaDependency = ideaDependency

        def host = StringUtil.trimStart(StringUtil.trimStart(StringUtil.trimStart(repositoryHost, 'http://'), 'https://'), 'www')
        // todo: a better way to define cache directory
        cacheDirectoryPath = Paths.get(gradleHomePath, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea', host).toString()
    }

    @NotNull
    PluginDependency resolve(@NotNull String id, @Nullable String version, @Nullable String channel) {
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
            // todo: implement downloading last compatible plugin version
            throw new BuildException("Cannot find builtin plugin $id for IDE: $ideaDependency.classes.absolutePath", null)
        }
        return findCachedPlugin(id, version, channel) ?: downloadPlugin(id, version, channel)
    }

    void register(@NotNull Project project, @NotNull PluginDependency plugin, @NotNull String configuration) {
        registerRepoIfNeeded(project, plugin)
        generateIvyFile(plugin)
        project.dependencies.add(configuration, [
            group: 'org.jetbrains.plugins', name: plugin.id, version: plugin.version, configuration: 'compile'
        ])
    }

    void registerRepoIfNeeded(@NotNull Project project, @NotNull PluginDependency plugin) {
        if (!plugin.builtin) {
            def artifactParent = plugin.artifact.parentFile
            if (artifactParent.parent != cacheDirectoryPath) {
                pluginSources.add(artifactParent.absolutePath)
            }
        }
        if (repoRegistered) return
        repoRegistered = true

        project.repositories.ivy { repo ->
            repo.ivyPattern("$cacheDirectoryPath/[module]-[revision].[ext]") // ivy xml
            repo.artifactPattern("$ideaDependency.classes/plugins/[module]/[artifact].[ext]") // builtin plugins
            repo.artifactPattern("$cacheDirectoryPath/[module]-[revision]/[artifact](.[ext])") // external plugins
            pluginSources.each {
                repo.artifactPattern("$it/[artifact].[ext]") // local plugins
            }
            if (ideaDependency.sources) {
                repo.artifactPattern("$ideaDependency.sources/[artifact]-[revision](-[classifier]).[ext]")
            }
        }
    }

    String getCacheDirectoryPath() {
        return cacheDirectoryPath
    }

    @NotNull
    private void generateIvyFile(@NotNull PluginDependency plugin) {
        def baseDir = plugin.builtin ? plugin.artifact : plugin.artifact.parentFile
        def pluginFqn = pluginFqn(plugin.id, plugin.version)
        def ivyFile = new File(cacheDirectoryPath, "${pluginFqn}.xml")
        if (!ivyFile.exists()) {
            def identity = new DefaultIvyPublicationIdentity("org.jetbrains.plugins", plugin.id, plugin.version)
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

    @NotNull
    private PluginDependency downloadPlugin(@NotNull String id, @Nullable String version, @Nullable String channel) {
        IntelliJPlugin.LOG.info("Downloading $id:$version from $repositoryHost")
        def repositoryInstance = new PluginRepositoryInstance(repositoryHost, null, null)
        def tempDirectory = Files.createTempDirectory("intellij")
        def download = repositoryInstance.download(id, version, channel, tempDirectory.toString())
        if (download == null) {
            throw new BuildException("Cannot find plugin $id:$version at $repositoryHost", null)
        }

        def cacheDirectory = pluginCache(id, version)
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw new BuildException("Cannot get access to cache directory: $cacheDirectory.absolutePath", null)
        }
        if (Utils.isJarFile(download)) {
            def artifactFile = new File(cacheDirectory, download.name)
            def move = Files.move(download.toPath(), artifactFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return externalPluginDependency(move.toFile(), channel)
        } else if (Utils.isZipFile(download)) {
            return externalPluginDependency(extractZip(id, version, download, cacheDirectory), channel)
        }
        throw new BuildException("Invalid type of downloaded plugin: $download", null)
    }

    @NotNull
    private static File extractZip(@NotNull String pluginId, @Nullable String version,
                                   @NotNull File pluginZip, @NotNull File targetDirectory) {
        def zipFile = new ZipFile(pluginZip)
        try {
            def entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                def entry = entries.nextElement()
                def path = entry.name
                if (!path) {
                    continue
                }
                File dest = new File(targetDirectory, path)
                if (entry.isDirectory()) {
                    if (!dest.exists() && !dest.mkdirs()) {
                        throw new BuildException("Cannot unzip plugin $pluginId:$version: $pluginZip.absolutePath", null)
                    }
                } else {
                    if (!dest.getParentFile().exists() && !dest.getParentFile().mkdirs()) {
                        throw new BuildException("Cannot unzip plugin $pluginId:$version: $pluginZip.absolutePath", null)
                    }
                    OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(dest))
                    try {
                        copyInputStream(zipFile.getInputStream(entry), outputStream)
                    } finally {
                        outputStream.close()
                    }
                }
            }
        } finally {
            zipFile.close()
        }
        return findArtifact(targetDirectory)
    }

    @Nullable
    private PluginDependency findCachedPlugin(@NotNull String id, @NotNull String version, @Nullable String channel) {
        def cache = null
        try {
            cache = pluginCache(id, version)
            if (cache.exists()) {
                return externalPluginDependency(findArtifact(cache), channel)
            }
        }
        catch (AssertionError ignored) {
            IntelliJPlugin.LOG.warn("Cannot read cached plugin $cache")
        }
        return null
    }

    private File pluginCache(@NotNull String pluginId, @NotNull String version) {
        return new File(cacheDirectoryPath, pluginFqn(pluginId, version))
    }

    @NotNull
    private static File findArtifact(File directory) {
        def files = directory.listFiles()
        if (files == null || files.length != 1) {
            throw new AssertionError("Single child expected in $directory")
        }
        return files[0]
    }

    private static externalPluginDependency(@NotNull File artifact, @Nullable String channel) {
        def creationResult = IdePluginManager.createManager().createPlugin(artifact)
        if (creationResult instanceof PluginCreationSuccess) {
            def intellijPlugin = creationResult.plugin
            def pluginDependency = new PluginDependencyImpl(intellijPlugin.pluginId, intellijPlugin.pluginVersion, artifact)
            pluginDependency.channel = channel
            pluginDependency.sinceBuild = intellijPlugin.sinceBuild?.asStringWithoutProductCode()
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

    private static void copyInputStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024]
        int len
        while ((len = input.read(buffer)) >= 0) {
            output.write(buffer, 0, len)
        }
        input.close()
        output.close()
    }

    private static pluginFqn(@NotNull String id, @NotNull String version) {
        "$id-$version"
    }
}

