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
import org.jetbrains.intellij.IntelliJIvyDescriptorFileGenerator
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.ideBuildNumber
import org.jetbrains.intellij.ideaDir
import org.jetbrains.intellij.info
import org.jetbrains.intellij.isKotlinRuntime
import org.jetbrains.intellij.releaseType
import org.jetbrains.intellij.unzip
import org.jetbrains.intellij.warn
import java.io.File
import java.net.URI
import java.util.zip.ZipFile

class IdeaDependencyManager(val repoUrl: String, val ideaDependencyCachePath: String?) {

    private val mainDependencies = listOf("ideaIC", "ideaIU", "riderRD", "riderRS")

    fun register(project: Project, dependency: IdeaDependency, dependencies: DependencySet) {
        val ivyFile = getOrCreateIvyXml(dependency)
        val ivyFileSuffix = ivyFile.name.substring("${dependency.name}-${dependency.version}".length).removeSuffix(".xml")
        project.repositories.ivy { repo ->
            repo.url = dependency.classes.toURI()
            repo.ivyPattern("${ivyFile.parent}/[module]-[revision]$ivyFileSuffix.[ext]") // ivy xml
            repo.artifactPattern("${dependency.classes.path}/[artifact].[ext]") // idea libs
            if (dependency.sources != null) {
                repo.artifactPattern("${dependency.sources.parent}/[artifact]-[revision]-[classifier].[ext]")
            }
        }
        dependencies.add(project.dependencies.create(mapOf(
            "group" to "com.jetbrains",
            "name" to dependency.name,
            "version" to dependency.version,
            "configuration" to "compile"
        )))
    }

    private fun createDependency(
        name: String, type: String?, version: String,
        buildNumber: String,
        classesDirectory: File, sourcesDirectory: File?, project: Project,
        extraDependencies: Collection<IdeaExtraDependency>,
    ): IdeaDependency {
        if (type == "JPS") {
            return JpsIdeaDependency(version, buildNumber, classesDirectory, sourcesDirectory, !hasKotlinDependency(project))
        } else if (type == null) {
            val pluginsRegistry = BuiltinPluginsRegistry.fromDirectory(File(classesDirectory, "plugins"), project)
            return LocalIdeaDependency(name,
                version,
                buildNumber,
                classesDirectory,
                sourcesDirectory,
                !hasKotlinDependency(project),
                pluginsRegistry,
                extraDependencies)
        }
        val pluginsRegistry = BuiltinPluginsRegistry.fromDirectory(File(classesDirectory, "plugins"), project)
        return IdeaDependency(name,
            version,
            buildNumber,
            classesDirectory,
            sourcesDirectory,
            !hasKotlinDependency(project),
            pluginsRegistry,
            extraDependencies)
    }

    private fun resolveSources(project: Project, version: String): File? {
        info(project, "Adding IDE sources repository")
        try {
            val dependency = project.dependencies.create("com.jetbrains.intellij.idea:ideaIC:$version:sources@jar")
            val sourcesConfiguration = project.configurations.detachedConfiguration(dependency)
            val sourcesFiles = sourcesConfiguration.files
            if (sourcesFiles.size == 1) {
                val sourcesDirectory = sourcesFiles.first()
                debug(project, "IDE sources jar: " + sourcesDirectory.path)
                return sourcesDirectory
            } else {
                warn(project, "Cannot attach IDE sources. Found files: $sourcesFiles")
            }
        } catch (e: ResolveException) {
            warn(project, "Cannot resolve IDE sources dependency", e)
        }
        return null
    }

    private fun unzipDependencyFile(
        cacheDirectory: File,
        project: Project,
        zipFile: File,
        type: String,
        checkVersionChange: Boolean,
    ) = unzip(zipFile, cacheDirectory, project, { markerFile ->
        isCacheUpToDate(zipFile, markerFile, checkVersionChange)
    }, { unzippedDirectory, markerFile ->
        resetExecutablePermissions(project, unzippedDirectory, type)
        storeCache(unzippedDirectory, markerFile)
    }, null)

    private fun isCacheUpToDate(zipFile: File, markerFile: File, checkVersion: Boolean): Boolean {
        if (!checkVersion) {
            return markerFile.exists()
        }
        if (!markerFile.exists()) {
            return false
        }
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry("build.txt")
            if (entry != null && zip.getInputStream(entry).bufferedReader().use { it.readText() } != markerFile.readText()) {
                return false
            }
        }
        return true
    }

    private fun storeCache(directoryToCache: File, markerFile: File) {
        val buildTxt = File(directoryToCache, "build.txt")
        if (buildTxt.exists()) {
            markerFile.writeText(buildTxt.readText().trim())
        }
    }

    private fun resetExecutablePermissions(project: Project, cacheDirectory: File, type: String) {
        if (type == "RD") {
            val operatingSystem = OperatingSystem.current()
            if (!operatingSystem.isWindows) {
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

    private fun setExecutable(project: Project, parent: File, child: String) {
        val file = File(parent, child)
        debug(project, "Resetting executable permissions for $file.path")
        file.setExecutable(true, true)
    }

    private fun getOrCreateIvyXml(dependency: IdeaDependency): File {
        val directory = dependency.getIvyRepositoryDirectory()
        val ivyFile = if (directory != null) {

            File(directory, "${dependency.getFqn()}.xml")
        } else {
            File.createTempFile(dependency.getFqn(), ".xml")
        }

        if (directory == null || !ivyFile.exists()) {
            val identity = DefaultIvyPublicationIdentity("com.jetbrains", dependency.name, dependency.version)
            val generator = IntelliJIvyDescriptorFileGenerator(identity)
            generator.addConfiguration(DefaultIvyConfiguration("default"))
            generator.addConfiguration(DefaultIvyConfiguration("compile"))
            generator.addConfiguration(DefaultIvyConfiguration("sources"))
            dependency.jarFiles.forEach {
                generator.addArtifact(IntellijIvyArtifact.createJarDependency(it, "compile", dependency.classes, null))
            }
            if (dependency.sources != null) {
                val artifact = IntellijIvyArtifact(dependency.sources, "ideaIC", "jar", "sources", "sources")
                artifact.conf = "sources"
                generator.addArtifact(artifact)
            }
            generator.writeTo(ivyFile)
        }
        return ivyFile
    }

    private fun hasKotlinDependency(project: Project): Boolean {
        val configurations = project.configurations
        val dependencies = configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).allDependencies +
            configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).allDependencies
        return dependencies.any { "org.jetbrains.kotlin" == it.group && isKotlinRuntime(it.name) }
    }

    fun resolveRemote(project: Project, version: String, type: String, sources: Boolean, extraDependencies: List<String>): IdeaDependency {
        val releaseType = releaseType(version)
        debug(project, "Adding IDE repository: $repoUrl/$releaseType")
        project.repositories.maven { it.url = URI.create("$repoUrl/$releaseType") }

        debug(project, "Adding IDE dependency")
        var dependencyGroup = "com.jetbrains.intellij.idea"
        var dependencyName = "ideaIC"
        var hasSources = sources
        if (type == "IU") {
            dependencyName = "ideaIU"
        } else if (type == "CL") {
            dependencyGroup = "com.jetbrains.intellij.clion"
            dependencyName = "clion"
        } else if (type == "PY" || type == "PC") {
            dependencyGroup = "com.jetbrains.intellij.pycharm"
            dependencyName = "pycharm$type"
        } else if (type == "GO") {
            dependencyGroup = "com.jetbrains.intellij.goland"
            dependencyName = "goland"
        } else if (type == "RD") {
            dependencyGroup = "com.jetbrains.intellij.rider"
            dependencyName = "riderRD"
            if (sources && releaseType == "snapshots") {
                warn(project, "IDE sources are not available for Rider SNAPSHOTS")
                hasSources = false
            }
        }
        val dependency = project.dependencies.create("$dependencyGroup:$dependencyName:$version")

        val configuration = project.configurations.detachedConfiguration(dependency)

        val classesDirectory = extractClassesFromRemoteDependency(project, configuration, type, version)
        info(project, "IDE dependency cache directory: $classesDirectory")
        val buildNumber = ideBuildNumber(classesDirectory)
        val sourcesDirectory = if (hasSources) {
            resolveSources(project, version)
        } else {
            null
        }
        val resolvedExtraDependencies = resolveExtraDependencies(project, version, extraDependencies)
        return createDependency(dependencyName,
            type,
            version,
            buildNumber,
            classesDirectory,
            sourcesDirectory,
            project,
            resolvedExtraDependencies)
    }

    fun resolveLocal(project: Project, localPath: String, localPathSources: String?): IdeaDependency {
        debug(project, "Adding local IDE dependency")
        val ideaDir = ideaDir(localPath)
        if (!ideaDir.exists() || !ideaDir.isDirectory) {
            throw BuildException("Specified localPath '$localPath' doesn't exist or is not a directory", null)
        }
        val buildNumber = ideBuildNumber(ideaDir)
        val sources = if (!localPathSources.isNullOrEmpty()) {
            File(localPathSources)
        } else {
            null
        }
        return createDependency("ideaLocal", null, buildNumber, buildNumber, ideaDir, sources, project, emptyList())
    }

    private fun extractClassesFromRemoteDependency(project: Project, configuration: Configuration, type: String, version: String): File {
        val zipFile = configuration.singleFile
        debug(project, "IDE zip: " + zipFile.path)
        return unzipDependencyFile(getZipCacheDirectory(zipFile, project, type), project, zipFile, type, version.endsWith("-SNAPSHOT"))
    }

    private fun getZipCacheDirectory(zipFile: File, project: Project, type: String): File {
        if (!ideaDependencyCachePath.isNullOrEmpty()) {
            val customCacheParent = File(ideaDependencyCachePath)
            if (customCacheParent.exists()) {
                return File(customCacheParent.absolutePath)
            }
        } else if (type == "RD" && OperatingSystem.current().isWindows) {
            return project.buildDir
        }
        return zipFile.parentFile
    }

    private fun resolveExtraDependencies(
        project: Project,
        version: String,
        extraDependencies: List<String>,
    ): Collection<IdeaExtraDependency> {
        if (extraDependencies.isEmpty()) {
            return emptyList()
        }
        info(project, "Configuring IDE extra dependencies $extraDependencies")
        val mainInExtraDeps = extraDependencies.filter { dep -> mainDependencies.any { it == dep } }
        if (mainInExtraDeps.isNotEmpty()) {
            throw GradleException("The items $mainInExtraDeps cannot be used as extra dependencies")
        }
        val resolvedExtraDependencies = ArrayList<IdeaExtraDependency>()
        extraDependencies.forEach {
            resolveExtraDependency(project, version, it)?.let { dependencyFile ->
                val extraDependency = IdeaExtraDependency(it, dependencyFile)
                debug(project, "IDE extra dependency $it in $dependencyFile files: ${extraDependency.jarFiles}")
                resolvedExtraDependencies.add(extraDependency)
            } ?: debug(project, "IDE extra dependency for $it was resolved as null")
        }
        return resolvedExtraDependencies
    }

    private fun resolveExtraDependency(project: Project, version: String, name: String): File? {
        try {
            val dependency = project.dependencies.create("com.jetbrains.intellij.idea:$name:$version")
            val extraDepConfiguration = project.configurations.detachedConfiguration(dependency)
            val files = extraDepConfiguration.files
            if (files.size == 1) {
                val depFile = files.first()
                if (depFile.name.endsWith(".zip")) {
                    val cacheDirectory = getZipCacheDirectory(depFile, project, "IC")
                    debug(project, "IDE extra dependency $name: " + cacheDirectory.path)
                    return unzipDependencyFile(cacheDirectory, project, depFile, "IC", version.endsWith("-SNAPSHOT"))
                } else {
                    debug(project, "IDE extra dependency $name: " + depFile.path)
                    return depFile
                }
            } else {
                warn(project, "Cannot attach IDE extra dependency $name. Found files: $files")
            }
        } catch (e: ResolveException) {
            warn(project, "Cannot resolve IDE extra dependency $name", e)
        }
        return null
    }
}
