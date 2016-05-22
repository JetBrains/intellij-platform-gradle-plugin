package org.jetbrains.intellij.dependency

import com.intellij.structure.domain.PluginManager
import com.intellij.structure.impl.utils.StringUtil
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.api.publish.ivy.internal.publisher.IvyDescriptorFileGenerator
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.Utils
import org.jetbrains.intellij.pluginRepository.PluginRepositoryInstance

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

class PluginDependencyManager {
    private static final String DEFAULT_INTELLIJ_PLUGINS_REPO = 'http://plugins.jetbrains.com'

    private final String cacheDirectoryPath
    private final String repositoryHost
    private final File ideaDirectory

    PluginDependencyManager(@NotNull String gradleHomePath, @Nullable File ideaDirectory) {
        this.repositoryHost = DEFAULT_INTELLIJ_PLUGINS_REPO
        this.ideaDirectory = ideaDirectory

        def host = StringUtil.trimStart(StringUtil.trimStart(StringUtil.trimStart(repositoryHost, 'http://'), 'https://'), 'www')
        cacheDirectoryPath = Paths.get(gradleHomePath, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea', host).toString()
    }

    PluginDependencyManager(@NotNull Project project) {
        this(project.gradle.gradleUserHomeDir.absolutePath, projectIdeaDir(project))
    }

    @NotNull
    PluginDependency resolve(@NotNull String id, @Nullable String version, @Nullable String channel) {
        if (!version && !channel) {
            if (Paths.get(id).absolute) {
                return externalPluginDependency(new File(id), null)
            }
            else if (ideaDirectory) {
                IntelliJPlugin.LOG.info("Looking for builtin $id in $ideaDirectory.absolutePath")
                def pluginDirectory = new File(ideaDirectory, "plugins/$id")
                if (pluginDirectory.exists() && pluginDirectory.isDirectory()) {
                    return new PluginDependency(id, Utils.ideaBuildNumber(ideaDirectory), pluginDirectory, true)
                }
            }
            // todo: implement downloading last compatible plugin version
            throw new BuildException("Cannot find builtin plugin $id for IDE: $ideaDirectory.absolutePath", null)
        }
        return findCachedPlugin(id, version, channel) ?: downloadPlugin(id, version, channel)
    }

    void register(@NotNull Project project, @NotNull PluginDependency plugin) {
        def baseDir = plugin.artifact.parentFile.parentFile // idea for builtin, cache dir for external
        def ivyFile = getOrCreateIvyXml(plugin, baseDir)
        project.repositories.ivy { repo ->
            repo.url = baseDir
            repo.ivyPattern(ivyFile.absolutePath) // ivy xml
            repo.artifactPattern("$baseDir.absolutePath/[artifact].[ext]") // jars
        }
        project.dependencies.add(JavaPlugin.COMPILE_CONFIGURATION_NAME, [
                group: 'org.jetbrains.plugins', name: plugin.id, version: plugin.version, configuration: 'compile'
        ])
    }

    String getCacheDirectoryPath() {
        return cacheDirectoryPath
    }

    private static File projectIdeaDir(@NotNull Project project) {
        def extension = project.extensions.getByType(IntelliJPluginExtension.class)
        return extension ? extension.ideaDirectory : null
    }

    @NotNull
    private File getOrCreateIvyXml(@NotNull PluginDependency plugin, @NotNull File baseDir) {
        def ivyFile = new File(cacheDirectoryPath, "${plugin.fqn}.xml")
        if (!ivyFile.exists()) {
            def identity = new DefaultIvyPublicationIdentity("org.jetbrains.plugins", plugin.id, plugin.version)
            def generator = new IvyDescriptorFileGenerator(identity)
            def configuration = new DefaultIvyConfiguration("compile")
            generator.addConfiguration(configuration)
            plugin.jarFiles.each { generator.addArtifact(Utils.createDependency(it, configuration.name, baseDir)) }
            generator.writeTo(ivyFile)
        }
        return ivyFile
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

        def cacheDirectory = pluginCache(id, version, channel)
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
                    OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(dest));
                    try {
                        copyInputStream(zipFile.getInputStream(entry), outputStream);
                    } finally {
                        outputStream.close();
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
            cache = pluginCache(id, version, channel)
            if (cache.exists()) {
                return externalPluginDependency(findArtifact(cache), channel)
            }
        }
        catch (AssertionError ignored) {
            IntelliJPlugin.LOG.warn("Cannot read cached plugin $cache")
        }
        return null
    }

    private File pluginCache(@NotNull String pluginId, @NotNull String version, @Nullable String channel) {
        return new File(cacheDirectoryPath, PluginDependency.pluginFqn(pluginId, version, channel))
    }

    @NotNull
    private static File findArtifact(File directory) {
        def files = directory.listFiles()
        if (files == null || files.length != 1) {
            throw new AssertionError("Single child expected in $directory");
        }
        return files[0]
    }

    private static def externalPluginDependency(@NotNull File artifact, @Nullable String channel) {
        def intellijPlugin = PluginManager.instance.createPluginWithEmptyResolver(artifact)
        def pluginDependency = new PluginDependency(intellijPlugin.pluginId, intellijPlugin.pluginVersion, artifact)
        pluginDependency.channel = channel
        pluginDependency.sinceBuild = intellijPlugin.sinceBuild?.asString(false, false)
        pluginDependency.untilBuild = intellijPlugin.untilBuild?.asString(false, false)
        return pluginDependency
    }
    
     private static void copyInputStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = input.read(buffer)) >= 0) {
            output.write(buffer, 0, len);
        }
        input.close();
        output.close();
    }
}

