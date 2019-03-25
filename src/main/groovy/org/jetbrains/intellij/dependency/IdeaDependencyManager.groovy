package org.jetbrains.intellij.dependency

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.internal.os.OperatingSystem
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.Utils

import java.util.zip.ZipFile

import static org.jetbrains.intellij.IntelliJPlugin.LOG

class IdeaDependencyManager {
    private final String repoUrl
    private static final String[] mainDependencies = ["ideaIC", "ideaIU", "riderRD", "riderRS"]

    IdeaDependencyManager(@NotNull String repoUrl) {
        this.repoUrl = repoUrl
    }

    @NotNull
    IdeaDependency resolveRemote(@NotNull Project project, @NotNull String version, @NotNull String type, boolean sources,
                                 @NotNull Object[] extraDependencies) {
        def releaseType = Utils.releaseType(version)
        LOG.debug("Adding IntelliJ IDEA repository: ${repoUrl}/$releaseType")
        project.repositories.maven { it.url = "${repoUrl}/$releaseType" }

        LOG.debug("Adding IntelliJ IDEA dependency")
        def dependencyGroup = 'com.jetbrains.intellij.idea'
        def dependencyName = 'ideaIC'
        if (type == 'IU') {
            dependencyName = 'ideaIU'
        } else if (type == 'CL') {
            dependencyGroup = 'com.jetbrains.intellij.clion'
            dependencyName = 'clion'
        } else if (type == 'RD') {
            dependencyGroup = 'com.jetbrains.intellij.rider'
            dependencyName = 'riderRD'
        } else if (type == 'MPS') {
            dependencyGroup = 'com.jetbrains.mps'
            dependencyName = 'mps'
        }
        def dependency = project.dependencies.create("$dependencyGroup:$dependencyName:$version")

        def configuration = project.configurations.detachedConfiguration(dependency)

        def classesDirectory = extractClassesFromRemoteDependency(project, configuration, type, version)
        LOG.info("IntelliJ IDEA dependency cache directory: $classesDirectory")
        def buildNumber = Utils.ideaBuildNumber(classesDirectory)
        def sourcesDirectory = sources ? resolveSources(project, version) : null
        def resolvedExtraDependencies = resolveExtraDependencies(project, version, extraDependencies)
        return createDependency(dependencyName, type, version, buildNumber, classesDirectory, sourcesDirectory, project, resolvedExtraDependencies)
    }


    @NotNull
    IdeaDependency resolveLocal(@NotNull Project project, @NotNull String localPath, @Nullable String localPathSources) {
        LOG.debug("Adding local IDE dependency")
        def ideaDir = Utils.ideaDir(localPath)
        if (!ideaDir.exists() || !ideaDir.isDirectory()) {
            throw new BuildException("Specified localPath '$localPath' doesn't exist or is not a directory", null)
        }
        def buildNumber = Utils.ideaBuildNumber(ideaDir)
        def sources = localPathSources ? new File(localPathSources) : null
        return createDependency("ideaLocal", null, buildNumber, buildNumber, ideaDir, sources, project, Collections.emptyList())
    }

    static void register(@NotNull Project project, @NotNull IdeaDependency dependency, @NotNull String configuration) {
        def ivyFile = getOrCreateIvyXml(dependency, project.getGradle())
        def ivyFileSuffix = ivyFile.name.substring("${dependency.name}-${dependency.version}".length()) - ".xml"
        project.repositories.ivy { repo ->
            repo.url = dependency.classes
            repo.ivyPattern("$ivyFile.parent/[module]-[revision]$ivyFileSuffix.[ext]") // ivy xml
            repo.artifactPattern("$dependency.classes.path/[artifact].[ext]") // idea libs
            if (dependency.sources) {
                repo.artifactPattern("$dependency.sources.parent/[artifact]-[revision]-[classifier].[ext]")
            }
        }
        project.dependencies.add(configuration, [
            group: 'com.jetbrains', name: dependency.name, version: dependency.version, configuration: 'compile'
        ])
    }

    static boolean isKotlinRuntime(name) {
        return 'kotlin-runtime' == name || 'kotlin-reflect' == name || name.startsWith('kotlin-stdlib')
    }

    @NotNull
    private static IdeaDependency createDependency(String name, String type, String version,
                                                   String buildNumber,
                                                   File classesDirectory, File sourcesDirectory, Project project,
                                                   Collection<IdeaExtraDependency> extraDependencies) {
        if (type == 'JPS') {
            return new JpsIdeaDependency(version, buildNumber, classesDirectory, sourcesDirectory,
                    !hasKotlinDependency(project))
        } else if (type == null) {
            return new LocalIdeaDependency(name, version, buildNumber, classesDirectory, sourcesDirectory,
                    !hasKotlinDependency(project), extraDependencies)
        }
        return new IdeaDependency(name, version, buildNumber, classesDirectory, sourcesDirectory,
                !hasKotlinDependency(project), extraDependencies)
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
    private static File extractClassesFromRemoteDependency(@NotNull Project project,
                                                           @NotNull Configuration configuration,
                                                           @NotNull String type,
                                                           @NotNull String version) {
        File zipFile = configuration.singleFile
        LOG.debug("IDEA zip: " + zipFile.path)
        return unzipDependencyFile(getZipCacheDirectory(zipFile, project, type), project, zipFile, type, version.endsWith("-SNAPSHOT"))
    }

    @NotNull
    private static File getZipCacheDirectory(@NotNull File zipFile, @NotNull Project project, @NotNull String type) {
        def intellijExtension = project.extensions.findByType(IntelliJPluginExtension.class)
        if (intellijExtension && intellijExtension.ideaDependencyCachePath) {
            def customCacheParent = new File(intellijExtension.ideaDependencyCachePath)
            if (customCacheParent.exists()) {
                return new File(customCacheParent.absolutePath)
            }
        } else if (type == 'RD' && OperatingSystem.current().isWindows()) {
            return project.buildDir
        }
        return zipFile.parentFile
    }

    @NotNull
    private static Collection<IdeaExtraDependency> resolveExtraDependencies(@NotNull Project project,
                                                                            @NotNull String version,
                                                                            @NotNull Object[] extraDependencies) {
        LOG.info("Configuring IntelliJ IDEA extra dependencies $extraDependencies")
        def mainInExtraDeps = extraDependencies.findAll { dep -> mainDependencies.any { it == dep } }
        if (!mainInExtraDeps.empty) {
            throw new GradleException("The items $mainInExtraDeps cannot be used as extra dependencies")
        }
        def resolvedExtraDependencies = new ArrayList<IdeaExtraDependency>()
        extraDependencies.each {
            def name = it as String
            def dependencyFile = resolveExtraDependency(project, version, name)
            def extraDependency = new IdeaExtraDependency(name, dependencyFile)
            LOG.debug("IntelliJ IDEA extra dependency $name in $dependencyFile files: ${extraDependency.jarFiles}")
            resolvedExtraDependencies.add(extraDependency)
        }
        return resolvedExtraDependencies
    }

    @Nullable
    private static File resolveExtraDependency(@NotNull Project project, @NotNull String version, @NotNull String name) {
        try {
            def dependency = project.dependencies.create("com.jetbrains.intellij.idea:$name:$version")
            def extraDepConfiguration = project.configurations.detachedConfiguration(dependency)
            def files = extraDepConfiguration.files
            if (files.size() == 1) {
                File depFile = files.first()
                if (depFile.name.endsWith(".zip")) {
                    def cacheDirectory = getZipCacheDirectory(depFile, project, "IC")
                    LOG.debug("IDEA extra dependency $name: " + cacheDirectory.path)
                    return unzipDependencyFile(cacheDirectory, project, depFile, "IC", version.endsWith("-SNAPSHOT"))
                } else {
                    LOG.debug("IDEA extra dependency $name: " + depFile.path)
                    return depFile
                }
            } else {
                LOG.warn("Cannot attach IDEA extra dependency $name. Found files: " + files)
            }
        } catch (ResolveException e) {
            LOG.warn("Cannot resolve IDEA extra dependency $name", e)
        }
        return null
    }

    @NotNull
    private static File unzipDependencyFile(@NotNull File cacheDirectory,
                                            @NotNull Project project,
                                            @NotNull File zipFile,
                                            @NotNull String type,
                                            boolean checkVersionChange) {
        return Utils.unzip(zipFile, cacheDirectory, project, {
            markerFile -> isCacheUpToDate(zipFile, markerFile, checkVersionChange)
        }, { unzippedDirectory, markerFile ->
            resetExecutablePermissions(unzippedDirectory, type)
            storeCache(unzippedDirectory, markerFile)
        })
    }

    private static boolean isCacheUpToDate(File zipFile, File markerFile, boolean checkVersion) {
        if (!checkVersion) {
            return markerFile.exists()
        }
        if (!markerFile.exists()) {
            return false
        }
        def zip
        try {
            zip = new ZipFile(zipFile)
            def entry = zip.getEntry("build.txt")
            if (entry != null && zip.getInputStream(entry).text.trim() != markerFile.text.trim()) {
                return false
            }
        }
        finally {
            if (zip) {
                zip.close()
            }
        }
        return true
    }

    private static void storeCache(File directoryToCache, File markerFile) {
        def buildTxt = new File(directoryToCache, "build.txt")
        if (buildTxt.exists()) {
            markerFile.text = buildTxt.text.trim()
        }
    }

    private static void resetExecutablePermissions(@NotNull File cacheDirectory, @NotNull String type) {
        if (type == 'RD') {
            LOG.debug("Resetting executable permissions")
            def operatingSystem = OperatingSystem.current()
            if (!operatingSystem.isWindows()) {
                setExecutable(cacheDirectory, "lib/ReSharperHost/dupfinder.sh")
                setExecutable(cacheDirectory, "lib/ReSharperHost/inspectcode.sh")
                setExecutable(cacheDirectory, "lib/ReSharperHost/JetBrains.ReSharper.Host.sh")
                setExecutable(cacheDirectory, "lib/ReSharperHost/runtime.sh")
                setExecutable(cacheDirectory, "lib/ReSharperHost/macos-x64/mono/bin/env-wrapper")
                setExecutable(cacheDirectory, "lib/ReSharperHost/macos-x64/mono/bin/mono-sgen")
                setExecutable(cacheDirectory, "lib/ReSharperHost/macos-x64/mono/bin/mono-sgen-gdb.py")
                setExecutable(cacheDirectory, "lib/ReSharperHost/linux-x64/mono/bin/mono-sgen")
                setExecutable(cacheDirectory, "lib/ReSharperHost/linux-x64/mono/bin/mono-sgen-gdb.py")
            }
        }
    }

    static def setExecutable(File parent, String child) {
        new File(parent, child).setExecutable(true, true)
    }

    private static File getOrCreateIvyXml(@NotNull IdeaDependency dependency, @NotNull Gradle gradle) {
        def directory = dependency.getIvyRepositoryDirectory()
        File ivyFile = directory != null ? new File(directory, "${dependency.fqn}.xml") : File.createTempFile(dependency.fqn, ".xml")
        if (directory == null || !ivyFile.exists()) {
            def identity = new DefaultIvyPublicationIdentity("com.jetbrains", dependency.name, dependency.version)
            def generator = Utils.createIvyFileGenerator(identity, gradle)
            generator.addConfiguration(new DefaultIvyConfiguration("default"))
            generator.addConfiguration(new DefaultIvyConfiguration("compile"))
            generator.addConfiguration(new DefaultIvyConfiguration("sources"))
            dependency.jarFiles.each {
                generator.addArtifact(Utils.createJarDependency(it, "compile", dependency.classes))
            }
            if (dependency.sources) {
                def artifact = new IntellijIvyArtifact(dependency.sources, 'ideaIC', "jar", "sources", "sources")
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
                return isKotlinRuntime(it.name)
            }
            return false
        }
        return configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).getAllDependencies().find(closure) ||
                configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).getAllDependencies().find(closure)
    }
}