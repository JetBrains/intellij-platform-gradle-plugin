package org.jetbrains.intellij.dependency

import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations
import org.gradle.tooling.BuildException
import org.jetbrains.intellij.*
import java.io.File
import java.net.URI
import java.util.zip.ZipFile
import javax.inject.Inject

@Incubating
open class IdeaDependencyManager @Inject constructor(
    private val repositoryUrl: String,
    private val ideaDependencyCachePath: String,
    private val context: String?,
    private val archiveOperations: ArchiveOperations,
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) {

    private val mainDependencies = listOf("ideaIC", "ideaIU", "riderRD", "riderRS")

    fun register(project: Project, dependency: IdeaDependency, dependencies: DependencySet) {
        val ivyFile = getOrCreateIvyXml(dependency)
        val ivyFileSuffix = ivyFile.name.substring("${dependency.name}-${dependency.version}".length).removeSuffix(".xml")
        project.repositories.ivy {
            it.url = dependency.classes.toURI()
            it.ivyPattern("${ivyFile.parent}/[module]-[revision]$ivyFileSuffix.[ext]") // ivy xml
            it.artifactPattern("${dependency.classes.path}/[artifact].[ext]") // idea libs
            if (dependency.sources != null) {
                it.artifactPattern("${dependency.sources.parent}/[artifact]-[revision]-[classifier].[ext]")
            }
        }
        dependencies.add(project.dependencies.create(
            group = "com.jetbrains",
            name = dependency.name,
            version = dependency.version,
            configuration = "compile",
        ))
    }

    private fun createDependency(
        name: String,
        type: String?,
        version: String,
        buildNumber: String,
        classesDirectory: File,
        sourcesDirectory: File?,
        project: Project,
        extraDependencies: Collection<IdeaExtraDependency>,
    ) = when (type) {
        "JPS" -> JpsIdeaDependency(version, buildNumber, classesDirectory, sourcesDirectory, !hasKotlinDependency(project), context)
        else -> {
            val pluginsRegistry = BuiltinPluginsRegistry.fromDirectory(File(classesDirectory, "plugins"), context)
            when (type) {
                null -> LocalIdeaDependency(
                    name,
                    version,
                    buildNumber,
                    classesDirectory,
                    sourcesDirectory,
                    !hasKotlinDependency(project),
                    pluginsRegistry,
                    extraDependencies,
                )
                else -> IdeaDependency(
                    name,
                    version,
                    buildNumber,
                    classesDirectory,
                    sourcesDirectory,
                    !hasKotlinDependency(project),
                    pluginsRegistry,
                    extraDependencies,
                )
            }
        }
    }

    private fun resolveSources(project: Project, version: String, type: String): File? {
        info(context, "Adding IDE sources repository")
        try {
            val forPyCharm = isPyCharmType(type)
            val dependency = project.dependencies.create(
                group = if (forPyCharm) "com.jetbrains.intellij.pycharm" else "com.jetbrains.intellij.idea",
                name = if (forPyCharm) "pycharmPC" else "ideaIC",
                version = version,
                classifier = "sources",
                extension = "jar",
            )
            val sourcesConfiguration = project.configurations.detachedConfiguration(dependency)
            val sourcesFiles = sourcesConfiguration.files
            if (sourcesFiles.size == 1) {
                val sourcesDirectory = sourcesFiles.first()
                debug(context, "IDE sources jar: " + sourcesDirectory.path)
                return sourcesDirectory
            } else {
                warn(context, "Cannot attach IDE sources. Found files: $sourcesFiles")
            }
        } catch (e: ResolveException) {
            warn(context, "Cannot resolve IDE sources dependency", e)
        }
        return null
    }

    private fun unzipDependencyFile(
        cacheDirectory: File,
        zipFile: File,
        type: String,
        checkVersionChange: Boolean,
    ) = extractArchive(
        zipFile,
        cacheDirectory.resolve(zipFile.name.removeSuffix(".zip")),
        archiveOperations,
        execOperations,
        fileSystemOperations,
        context,
        { markerFile -> isCacheUpToDate(zipFile, markerFile, checkVersionChange) },
        { unzippedDirectory, markerFile ->
            resetExecutablePermissions(unzippedDirectory, type)
            storeCache(unzippedDirectory, markerFile)
        },
    )

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
        File(directoryToCache, "build.txt").takeIf { it.exists() }?.let {
            markerFile.writeText(it.readText().trim())
        }
    }

    private fun resetExecutablePermissions(cacheDirectory: File, type: String) {
        if (type == "RD" && !OperatingSystem.current().isWindows) {
            setExecutable(cacheDirectory, "lib/ReSharperHost/dupfinder.sh", context)
            setExecutable(cacheDirectory, "lib/ReSharperHost/inspectcode.sh", context)
            setExecutable(cacheDirectory, "lib/ReSharperHost/JetBrains.ReSharper.Host.sh", context)
            setExecutable(cacheDirectory, "lib/ReSharperHost/runtime.sh", context)
            setExecutable(cacheDirectory, "lib/ReSharperHost/macos-x64/mono/bin/env-wrapper", context)
            setExecutable(cacheDirectory, "lib/ReSharperHost/macos-x64/mono/bin/mono-sgen", context)
            setExecutable(cacheDirectory, "lib/ReSharperHost/macos-x64/mono/bin/mono-sgen-gdb.py", context)
            setExecutable(cacheDirectory, "lib/ReSharperHost/linux-x64/mono/bin/mono-sgen", context)
            setExecutable(cacheDirectory, "lib/ReSharperHost/linux-x64/mono/bin/mono-sgen-gdb.py", context)
        }
    }

    private fun setExecutable(parent: File, child: String, context: String?) {
        File(parent, child).apply {
            debug(context, "Resetting executable permissions for: $path")
            setExecutable(true, true)
        }
    }

    private fun getOrCreateIvyXml(dependency: IdeaDependency): File {
        val directory = dependency.getIvyRepositoryDirectory()
        val ivyFile = when {
            directory != null -> File(directory, "${dependency.getFqn()}.xml")
            else -> File.createTempFile(dependency.getFqn(), ".xml")
        }

        if (directory == null || !ivyFile.exists()) {
            val identity = DefaultIvyPublicationIdentity("com.jetbrains", dependency.name, dependency.version)
            IntelliJIvyDescriptorFileGenerator(identity).apply {
                addConfiguration(DefaultIvyConfiguration("default"))
                addConfiguration(DefaultIvyConfiguration("compile"))
                addConfiguration(DefaultIvyConfiguration("sources"))

                dependency.jarFiles.forEach {
                    addArtifact(IntellijIvyArtifact.createJarDependency(it, "compile", dependency.classes, null))
                }

                if (dependency.sources != null) {
                    val name = if (isDependencyOnPyCharm(dependency)) "pycharmPC" else "ideaIC"
                    val artifact = IntellijIvyArtifact(dependency.sources, name, "jar", "sources", "sources")
                    artifact.conf = "sources"
                    addArtifact(artifact)
                }

                writeTo(ivyFile)
            }
        }
        return ivyFile
    }

    private fun hasKotlinDependency(project: Project) =
        project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).allDependencies.any {
            "org.jetbrains.kotlin" == it.group && isKotlinRuntime(it.name)
        }

    fun resolveRemote(project: Project, version: String, type: String, sources: Boolean, extraDependencies: List<String>): IdeaDependency {
        val releaseType = releaseType(version)
        debug(context, "Adding IDE repository: $repositoryUrl/$releaseType")
        project.repositories.maven { it.url = URI.create("$repositoryUrl/$releaseType") }

        debug(context, "Adding IDE dependency")
        var dependencyGroup = "com.jetbrains.intellij.idea"
        var dependencyName = "ideaIC"
        var hasSources = sources
        if (type == "IU") {
            dependencyName = "ideaIU"
        } else if (type == "CL") {
            dependencyGroup = "com.jetbrains.intellij.clion"
            dependencyName = "clion"
        } else if (isPyCharmType(type)) {
            dependencyGroup = "com.jetbrains.intellij.pycharm"
            dependencyName = "pycharm$type"
        } else if (type == "GO") {
            dependencyGroup = "com.jetbrains.intellij.goland"
            dependencyName = "goland"
        } else if (type == "RD") {
            dependencyGroup = "com.jetbrains.intellij.rider"
            dependencyName = "riderRD"
            if (sources && releaseType == "snapshots") {
                warn(context, "IDE sources are not available for Rider SNAPSHOTS")
                hasSources = false
            }
        } else if (type == "GW") {
            dependencyGroup = "com.jetbrains.gateway"
            dependencyName = "JetBrainsGateway"
            hasSources = false
        }
        val dependency = project.dependencies.create(
            group = dependencyGroup,
            name = dependencyName,
            version = version,
        )

        val configuration = project.configurations.detachedConfiguration(dependency)
        val classesDirectory = extractClassesFromRemoteDependency(project, configuration, type, version)
        info(context, "IDE dependency cache directory: $classesDirectory")
        val buildNumber = ideBuildNumber(classesDirectory)
        val sourcesDirectory = when {
            hasSources -> resolveSources(project, version, type)
            else -> null
        }
        val resolvedExtraDependencies = resolveExtraDependencies(project, version, extraDependencies)
        return createDependency(
            dependencyName,
            type,
            version,
            buildNumber,
            classesDirectory,
            sourcesDirectory,
            project,
            resolvedExtraDependencies,
        )
    }

    fun resolveLocal(project: Project, localPath: String, localPathSources: String?): IdeaDependency {
        debug(context, "Adding local IDE dependency")
        val ideaDir = ideaDir(localPath)
        if (!ideaDir.exists() || !ideaDir.isDirectory) {
            throw BuildException("Specified localPath '$localPath' doesn't exist or is not a directory", null)
        }
        val buildNumber = ideBuildNumber(ideaDir)
        val sources = when {
            !localPathSources.isNullOrEmpty() -> File(localPathSources)
            else -> null
        }
        return createDependency("ideaLocal", null, buildNumber, buildNumber, ideaDir, sources, project, emptyList())
    }

    private fun extractClassesFromRemoteDependency(project: Project, configuration: Configuration, type: String, version: String): File =
        configuration.singleFile.let {
            debug(context, "IDE zip: " + it.path)
            unzipDependencyFile(getZipCacheDirectory(it, project, type), it, type, version.endsWith("-SNAPSHOT"))
        }

    private fun getZipCacheDirectory(zipFile: File, project: Project, type: String): File {
        if (ideaDependencyCachePath.isNotEmpty()) {
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
        info(context, "Configuring IDE extra dependencies: $extraDependencies")
        extraDependencies
            .filter { dep -> mainDependencies.any { it == dep } }
            .takeIf { it.isNotEmpty() }
            ?.let { throw GradleException("The items '$it' cannot be used as extra dependencies") }

        val resolvedExtraDependencies = mutableListOf<IdeaExtraDependency>()
        extraDependencies.forEach {
            resolveExtraDependency(project, version, it)?.let { dependencyFile ->
                val extraDependency = IdeaExtraDependency(it, dependencyFile)
                debug(context, "IDE extra dependency '$it' in '$dependencyFile' files: ${extraDependency.jarFiles}")
                resolvedExtraDependencies.add(extraDependency)
            } ?: debug(context, "IDE extra dependency for '$it' was resolved as null")
        }
        return resolvedExtraDependencies
    }

    private fun resolveExtraDependency(project: Project, version: String, name: String): File? {
        try {
            val dependency = project.dependencies.create(
                group = "com.jetbrains.intellij.idea",
                name = name,
                version = version,
            )
            val extraDependencyConfiguration = project.configurations.detachedConfiguration(dependency)
            val files = extraDependencyConfiguration.files
            if (files.size == 1) {
                val depFile = files.first()
                return when {
                    depFile.name.endsWith(".zip") -> {
                        val cacheDirectory = getZipCacheDirectory(depFile, project, "IC")
                        debug(context, "IDE extra dependency '$name': " + cacheDirectory.path)
                        unzipDependencyFile(cacheDirectory, depFile, "IC", version.endsWith("-SNAPSHOT"))
                    }
                    else -> {
                        debug(context, "IDE extra dependency '$name': " + depFile.path)
                        depFile
                    }
                }
            } else {
                warn(context, "Cannot attach IDE extra dependency '$name'. Found files: $files")
            }
        } catch (e: ResolveException) {
            warn(context, "Cannot resolve IDE extra dependency '$name'", e)
        }
        return null
    }
}
