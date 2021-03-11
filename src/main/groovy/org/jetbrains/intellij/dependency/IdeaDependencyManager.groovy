package org.jetbrains.intellij.dependency

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.internal.os.OperatingSystem
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.IntelliJIvyDescriptorFileGenerator
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.Utils

import java.util.zip.ZipFile

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
        Utils.debug(project, "Adding IDE repository: ${repoUrl}/$releaseType")
        project.repositories.maven { it.url = "${repoUrl}/$releaseType" }

        Utils.debug(project, "Adding IDE dependency")
        def dependencyGroup = 'com.jetbrains.intellij.idea'
        def dependencyName = 'ideaIC'
        if (type == 'IU') {
            dependencyName = 'ideaIU'
        } else if (type == 'CL') {
            dependencyGroup = 'com.jetbrains.intellij.clion'
            dependencyName = 'clion'
        } else if (type == 'PY' || type == 'PC') {
            dependencyGroup = 'com.jetbrains.intellij.pycharm'
            dependencyName = 'pycharm' + type
        } else if (type == 'GO') {
            dependencyGroup = 'com.jetbrains.intellij.goland'
            dependencyName = 'goland'
        } else if (type == 'RD') {
            dependencyGroup = 'com.jetbrains.intellij.rider'
            dependencyName = 'riderRD'
            if (sources && releaseType == 'snapshots') {
                Utils.warn(project, "IDE sources are not available for Rider SNAPSHOTS")
                sources = false
            }
        }
        def dependency = project.dependencies.create("$dependencyGroup:$dependencyName:$version")

        def configuration = project.configurations.detachedConfiguration(dependency)

        def classesDirectory = extractClassesFromRemoteDependency(project, configuration, type, version)
        Utils.info(project, "IDE dependency cache directory: $classesDirectory")
        def buildNumber = Utils.ideBuildNumber(classesDirectory)
        def sourcesDirectory = sources ? resolveSources(project, version) : null
        def resolvedExtraDependencies = resolveExtraDependencies(project, version, extraDependencies)
        return createDependency(dependencyName, type, version, buildNumber, classesDirectory, sourcesDirectory, project, resolvedExtraDependencies)
    }


    @NotNull
    IdeaDependency resolveLocal(@NotNull Project project, @NotNull String localPath, @Nullable String localPathSources) {
        Utils.debug(project, "Adding local IDE dependency")
        def ideaDir = Utils.ideaDir(localPath)
        if (!ideaDir.exists() || !ideaDir.isDirectory()) {
            throw new BuildException("Specified localPath '$localPath' doesn't exist or is not a directory", null)
        }
        def buildNumber = Utils.ideBuildNumber(ideaDir)
        def sources = localPathSources ? new File(localPathSources) : null
        return createDependency("ideaLocal", null, buildNumber, buildNumber, ideaDir, sources, project, Collections.emptyList())
    }

    static void register(@NotNull Project project, @NotNull IdeaDependency dependency, @NotNull DependencySet dependencies) {
        def ivyFile = getOrCreateIvyXml(dependency)
        def ivyFileSuffix = ivyFile.name.substring("${dependency.name}-${dependency.version}".length()) - ".xml"
        project.repositories.ivy { repo ->
            repo.url = dependency.classes
            repo.ivyPattern("$ivyFile.parent/[module]-[revision]$ivyFileSuffix.[ext]") // ivy xml
            repo.artifactPattern("$dependency.classes.path/[artifact].[ext]") // idea libs
            if (dependency.sources) {
                repo.artifactPattern("$dependency.sources.parent/[artifact]-[revision]-[classifier].[ext]")
            }
        }
        dependencies.add(project.dependencies.create([
                group: 'com.jetbrains', name: dependency.name, version: dependency.version, configuration: 'compile'
        ]))
    }

    static boolean isKotlinRuntime(name) {
        return 'kotlin-runtime' == name ||
            name == 'kotlin-reflect' || name.startsWith('kotlin-reflect-') ||
            name == 'kotlin-stdlib' || name.startsWith('kotlin-stdlib-') ||
            name == 'kotlin-test' || name.startsWith('kotlin-test-')
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
            def pluginsRegistry = BuiltinPluginsRegistry.fromDirectory(new File(classesDirectory, "plugins"), project)
            return new LocalIdeaDependency(name, version, buildNumber, classesDirectory, sourcesDirectory,
                    !hasKotlinDependency(project), pluginsRegistry, extraDependencies)
        }
        def pluginsRegistry = BuiltinPluginsRegistry.fromDirectory(new File(classesDirectory, "plugins"), project)
        return new IdeaDependency(name, version, buildNumber, classesDirectory, sourcesDirectory,
                !hasKotlinDependency(project), pluginsRegistry, extraDependencies)
    }

    @Nullable
    private static File resolveSources(@NotNull Project project, @NotNull String version) {
        Utils.info(project, "Adding IDE sources repository")
        try {
            def dependency = project.dependencies.create("com.jetbrains.intellij.idea:ideaIC:$version:sources@jar")
            def sourcesConfiguration = project.configurations.detachedConfiguration(dependency)
            def sourcesFiles = sourcesConfiguration.files
            if (sourcesFiles.size() == 1) {
                File sourcesDirectory = sourcesFiles.first()
                Utils.debug(project, "IDE sources jar: " + sourcesDirectory.path)
                return sourcesDirectory
            } else {
                Utils.warn(project, "Cannot attach IDE sources. Found files: " + sourcesFiles)
            }
        } catch (ResolveException e) {
            Utils.warn(project, "Cannot resolve IDE sources dependency", e)
        }
        return null
    }

    @NotNull
    private static File extractClassesFromRemoteDependency(@NotNull Project project,
                                                           @NotNull Configuration configuration,
                                                           @NotNull String type,
                                                           @NotNull String version) {
        File zipFile = configuration.singleFile
        Utils.debug(project, "IDE zip: " + zipFile.path)
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
        if (extraDependencies.length == 0) {
            return []
        }
        Utils.info(project, "Configuring IDE extra dependencies $extraDependencies")
        def mainInExtraDeps = extraDependencies.findAll { dep -> mainDependencies.any { it == dep } }
        if (!mainInExtraDeps.empty) {
            throw new GradleException("The items $mainInExtraDeps cannot be used as extra dependencies")
        }
        def resolvedExtraDependencies = new ArrayList<IdeaExtraDependency>()
        extraDependencies.each {
            def name = it as String
            def dependencyFile = resolveExtraDependency(project, version, name)
            def extraDependency = new IdeaExtraDependency(name, dependencyFile)
            Utils.debug(project, "IDE extra dependency $name in $dependencyFile files: ${extraDependency.jarFiles}")
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
                    Utils.debug(project, "IDE extra dependency $name: " + cacheDirectory.path)
                    return unzipDependencyFile(cacheDirectory, project, depFile, "IC", version.endsWith("-SNAPSHOT"))
                } else {
                    Utils.debug(project, "IDE extra dependency $name: " + depFile.path)
                    return depFile
                }
            } else {
                Utils.warn(project, "Cannot attach IDE extra dependency $name. Found files: " + files)
            }
        } catch (ResolveException e) {
            Utils.warn(project, "Cannot resolve IDE extra dependency $name", e)
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
            resetExecutablePermissions(project, unzippedDirectory, type)
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

    private static void resetExecutablePermissions(@NotNull Project project, @NotNull File cacheDirectory, @NotNull String type) {
        if (type == 'RD') {
            def operatingSystem = OperatingSystem.current()
            if (!operatingSystem.isWindows()) {
                setExecutable(project, cacheDirectory, "lib/ReSharperHost/dupfinder.sh")
                setExecutable(project, cacheDirectory, "lib/ReSharperHost/inspectcode.sh")
                setExecutable(project, cacheDirectory, "lib/ReSharperHost/JetBrains.ReSharper.Host.sh")
                setExecutable(project, cacheDirectory, "lib/ReSharperHost/runtime.sh")
                setExecutable(project, cacheDirectory, "lib/ReSharperHost/macos-x64/mono/bin/env-wrapper")
                setExecutable(project, cacheDirectory, "lib/ReSharperHost/macos-x64/mono/bin/mono-sgen")
                setExecutable(project, cacheDirectory, "lib/ReSharperHost/macos-x64/mono/bin/mono-sgen-gdb.py")
                setExecutable(project, cacheDirectory, "lib/ReSharperHost/linux-x64/mono/bin/mono-sgen")
                setExecutable(project, cacheDirectory, "lib/ReSharperHost/linux-x64/mono/bin/mono-sgen-gdb.py")
            }
        }
    }

    static def setExecutable(@Nullable Project project, File parent, String child) {
        def file = new File(parent, child)
        Utils.debug(project, "Resetting executable permissions for $file.path")
        file.setExecutable(true, true)
    }

    private static File getOrCreateIvyXml(@NotNull IdeaDependency dependency) {
        def directory = dependency.getIvyRepositoryDirectory()
        File ivyFile = directory != null ? new File(directory, "${dependency.fqn}.xml") : File.createTempFile(dependency.fqn, ".xml")
        if (directory == null || !ivyFile.exists()) {
            def identity = new DefaultIvyPublicationIdentity("com.jetbrains", dependency.name, dependency.version)
            def generator = new IntelliJIvyDescriptorFileGenerator(identity)
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