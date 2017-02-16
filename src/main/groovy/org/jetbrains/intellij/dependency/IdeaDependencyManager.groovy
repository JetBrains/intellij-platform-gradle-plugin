package org.jetbrains.intellij.dependency

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifact
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.api.publish.ivy.internal.publisher.IvyDescriptorFileGenerator
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.Utils

import static org.jetbrains.intellij.IntelliJPlugin.LOG

class IdeaDependencyManager {
    private final String repoUrl

    IdeaDependencyManager(@NotNull String repoUrl) {
        this.repoUrl = repoUrl
    }

    @NotNull
    IdeaDependency resolveRemote(@NotNull Project project, @NotNull String version, @NotNull String type, boolean sources) {
        LOG.debug("Adding IntelliJ IDEA repository")
        def releaseType = version.contains('SNAPSHOT') ? 'snapshots' : 'releases'
        project.repositories.maven { it.url = "${repoUrl}/$releaseType" }

        LOG.debug("Adding IntelliJ IDEA dependency")
        def dependencyGroup = 'com.jetbrains.intellij.idea'
        def dependencyName = 'ideaIC'
        if (type == 'IU') {
            dependencyName = 'ideaIU'
        } else if (type == 'RS') {
            dependencyGroup = 'com.jetbrains.intellij.rider'
            dependencyName = 'riderRS'
        }
        def dependency = project.dependencies.create("$dependencyGroup:$dependencyName:$version")
        def configuration = project.configurations.detachedConfiguration(dependency)

        def classesDirectory = getClassesDirectory(project, configuration, type)
        def buildNumber = Utils.ideaBuildNumber(classesDirectory)
        def sourcesDirectory = sources ? resolveSources(project, version) : null
        return createDependency(dependencyName, type, version, buildNumber, classesDirectory, sourcesDirectory, project)
    }

    @NotNull
    IdeaDependency resolveLocal(@NotNull Project project, @NotNull String localPath) {
        LOG.debug("Adding local IDE dependency")
        def ideaDir = new File(localPath)
        if (!ideaDir.exists() || !ideaDir.isDirectory()) {
            throw new BuildException("Specified localPath '$localPath' is not path to IntelliJ IDE", null)
        }
        def buildNumber = Utils.ideaBuildNumber(ideaDir)
        return createDependency("ideaLocal", null, buildNumber, buildNumber, ideaDir, null, project)
    }

    static void register(@NotNull Project project, @NotNull IdeaDependency dependency) {
        def ivyFile = getOrCreateIvyXml(dependency)
        project.repositories.ivy { repo ->
            repo.url = dependency.classes
            repo.ivyPattern(ivyFile.absolutePath) // ivy xml
            repo.artifactPattern("$dependency.classes.path/[artifact].[ext]") // idea libs
            if (dependency.sources) {
                repo.artifactPattern("$dependency.sources.parent/[artifact]-$dependency.version-[classifier].[ext]")
            }
        }
        project.dependencies.add(JavaPlugin.COMPILE_CONFIGURATION_NAME, [
                group: 'com.jetbrains', name: dependency.name, version: dependency.version, configuration: 'compile'
        ])
    }

    @NotNull
    private static IdeaDependency createDependency(String name, String type, String version,
                                                   String buildNumber,
                                                   File classesDirectory, File sourcesDirectory, Project project) {
        if (type == 'JPS') {
            return new JpsIdeaDependency(version, buildNumber, classesDirectory, sourcesDirectory,
                    !hasKotlinDependency(project))
        }
        return new IdeaDependency(name, version, buildNumber, classesDirectory, sourcesDirectory,
                !hasKotlinDependency(project))
    }

    @Nullable
    private static File resolveSources(@NotNull Project project, @NotNull String version) {
        LOG.info("Adding IntelliJ IDEA sources repository")
        try {
            def dependency = project.dependencies.create("com.jetbrains.intellij.idea:ideaIC:$version:sources@jar")
            def sourcesConfiguration = project.configurations.detachedConfiguration(dependency)
            def sourcesFiles = sourcesConfiguration.files
            if (sourcesFiles.size() == 1) {
                File sourcesDirectory = sourcesFiles.first()
                LOG.debug("IDEA sources jar: " + sourcesDirectory.path)
                return sourcesDirectory
            } else {
                LOG.warn("Cannot attach IDEA sources. Found files: " + sourcesFiles)
            }
        } catch (ResolveException e) {
            LOG.warn("Cannot resolve IDEA sources dependency", e)
        }
        return null
    }

    @NotNull
    private static File getClassesDirectory(
            @NotNull Project project, @NotNull Configuration configuration, @NotNull String type) {
        File zipFile = configuration.singleFile
        LOG.debug("IDEA zip: " + zipFile.path)
        def directoryName = zipFile.name - ".zip"

        def cacheParentDirectoryPath = zipFile.parent
        def intellijExtension = project.extensions.findByType(IntelliJPluginExtension.class)
        if (intellijExtension && intellijExtension.ideaDependencyCachePath) {
            def customCacheParent = new File(intellijExtension.ideaDependencyCachePath)
            if (customCacheParent.exists()) {
                cacheParentDirectoryPath = customCacheParent.absolutePath
            }
        } else if (type == 'RS') {
            cacheParentDirectoryPath = project.buildDir
        }
        def cacheDirectory = new File(cacheParentDirectoryPath, directoryName)
        def markerFile = new File(cacheDirectory, "markerFile")
        if (!markerFile.exists()) {
            if (cacheDirectory.exists()) cacheDirectory.deleteDir()
            cacheDirectory.mkdir()
            LOG.debug("Unzipping idea")
            project.copy {
                it.from(project.zipTree(zipFile))
                it.into(cacheDirectory)
            }
            markerFile.createNewFile()
            LOG.debug("Unzipped")
        }
        return cacheDirectory
    }

    private static File getOrCreateIvyXml(@NotNull IdeaDependency dependency) {
        def ivyFile = new File(dependency.classes, "${dependency.fqn}.xml")
        if (!ivyFile.exists()) {
            def generator = new IvyDescriptorFileGenerator(new DefaultIvyPublicationIdentity("com.jetbrains", dependency.name, dependency.version))
            generator.addConfiguration(new DefaultIvyConfiguration("default"))
            generator.addConfiguration(new DefaultIvyConfiguration("compile"))
            generator.addConfiguration(new DefaultIvyConfiguration("sources"))
            dependency.jarFiles.each {
                generator.addArtifact(Utils.createJarDependency(it, "compile", dependency.classes))
            }
            if (dependency.sources) {
                def artifact = new DefaultIvyArtifact(dependency.sources, 'ideaIC', "jar", "sources", "sources")
                artifact.conf = "sources"
                generator.addArtifact(artifact)
            }
            generator.writeTo(ivyFile)
        }
        return ivyFile
    }

    private static def hasKotlinDependency(@NotNull Project project) {
        def configurations = project.configurations
        def closure = {
            if ("org.jetbrains.kotlin" == it.group) {
                return "kotlin-runtime" == it.name || it.name.startsWith('kotlin-stdlib') || "kotlin-reflect" == it.name
            }
            return false
        }
        return configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).getAllDependencies().find(closure) ||
                configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME).getAllDependencies().find(closure)
    }
}


