package org.jetbrains.intellij

import com.google.common.base.Predicates
import com.intellij.structure.domain.IdeVersion
import com.intellij.structure.domain.Plugin
import com.intellij.structure.domain.PluginManager
import com.intellij.structure.impl.utils.JarsUtils
import com.intellij.structure.impl.utils.StringUtil
import org.apache.commons.io.IOUtils
import org.apache.tools.zip.ZipFile
import org.gradle.api.Project
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.pluginRepository.PluginRepositoryInstance

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class ExternalPluginRepository {

    private static final String DEFAULT_INTELLIJ_PLUGINS_REPO = 'http://plugins.jetbrains.com'

    private final String gradleHome
    private final String repositoryHost

    ExternalPluginRepository(Project project) {
        this(DEFAULT_INTELLIJ_PLUGINS_REPO, project.gradle.gradleUserHomeDir.absolutePath)
    }
    
    ExternalPluginRepository(@NotNull String repositoryHost, @NotNull String gradleHomePath) {
        this.repositoryHost = repositoryHost
        this.gradleHome = gradleHomePath 
    }
    

    ExternalPlugin findPlugin(@NotNull String pluginId, @NotNull String version, @Nullable String channel) {
        def plugin = findCachedPlugin(pluginId, version, channel)
        if (plugin == null) {
            IntelliJPlugin.LOG.info("Downloading $pluginId:$version from $repositoryHost")
            def repositoryInstance = new PluginRepositoryInstance(repositoryHost, null, null)
            def tempDirectory = Files.createTempDirectory("intellij")
            def download = repositoryInstance.download(pluginId, version, channel, tempDirectory.toString())
            if (download == null) {
                throw new BuildException("Cannot find plugin $pluginId:$version at $repositoryHost", null)
            }

            if (Utils.isJarFile(download)) {
                def targetFile = pathToPluginCache(pluginId, version, channel).resolve(download.name)
                targetFile.toFile().mkdirs()
                def move = Files.move(download.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING)
                plugin = new ExternalPlugin(move.toFile())
            } else if (Utils.isZipFile(download)) {
                def targetDirectory = pathToPluginCache(pluginId, version, channel).toFile()
                extractZip(pluginId, version, download, targetDirectory)
                plugin = new ExternalPlugin(targetDirectory)
            } else {
                throw new BuildException("Invalid type of downloaded plugin: $download", null)
            }
        }
        return plugin
    }

    private static File extractZip(String pluginId, String version, File file, File targetDirectory) {
        def zipFile = new ZipFile(file)
        def entries = zipFile.entries
        while (entries.hasMoreElements()) {
            def entry = entries.nextElement()
            def path = entry.name
            if (!path) {
                continue
            }
            File dest = new File(targetDirectory, path)
            if (entry.isDirectory()) {
                if (!dest.exists() && !dest.mkdirs()) {
                    throw new BuildException("Cannot unzip plugin $pluginId:$version", null)
                }
            } else {
                if (!dest.getParentFile().exists() && !dest.getParentFile().mkdirs()) {
                    throw new BuildException("Cannot unzip plugin $pluginId:$version", null)
                }
                InputStream input = zipFile.getInputStream(entry);
                OutputStream output = new FileOutputStream(dest);
                IOUtils.copy(input, output);
                IOUtils.closeQuietly(input);
                output.close();
            }
        }
        return targetDirectory
    }

    @NotNull
    File getCacheDirectory() {
        def result = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/')
        if (!result.exists() && !result.mkdirs()) {
            throw new BuildException("Cannot get access to $result", null)
        }
        return result
    }

    @Nullable
    ExternalPlugin findCachedPlugin(String pluginId, String version, String channel) {
        def cache = null
        try {
            cache = pathToPluginCache(pluginId, version, channel)
            if (Files.exists(cache)) {
                return new ExternalPlugin(cache.toFile())
            }
        }
        catch (AssertionError ignored) {
            IntelliJPlugin.LOG.warn("Cannot read cached plugin $cache")
        }
        return null
    }

    private Path pathToPluginCache(String pluginId, String version, String channel) {
        def cache = getCacheDirectory()
        def host = StringUtil.trimStart(StringUtil.trimStart(StringUtil.trimStart(repositoryHost, 'http://'), 'https://'), 'www')
        return Paths.get(cache.getAbsolutePath(), host, "$pluginId-${channel ?: 'master'}-$version/")
    }
}

public class ExternalPlugin {
    private final Plugin plugin
    private final File artifact

    ExternalPlugin(@NotNull File file) {
        this.artifact = Utils.singleChildIn(file)
        this.plugin = PluginManager.instance.createPluginWithEmptyResolver(artifact)
    }

    def isCompatible(@NotNull IdeVersion ideVersion) {
        return plugin.isCompatibleWithIde(ideVersion)
    }

    def getJarFiles() {
        if (Utils.isJarFile(artifact)) {
            return Collections.singletonList(artifact)
        }
        if (artifact.isDirectory()) {
            File lib = new File(artifact, "lib");
            if (lib.isDirectory()) {
                return JarsUtils.collectJars(lib, Predicates.<File> alwaysTrue(), true)
            }
        }
        return Collections.emptySet()
    }

    def getArtifact() {
        return artifact
    }
}