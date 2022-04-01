package org.jetbrains.intellij.dependency

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.create
import org.gradle.tooling.BuildException
import org.jetbrains.intellij.IntelliJIvyDescriptorFileGenerator
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_SUFFIX_SNAPSHOT
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_TYPE_SNAPSHOTS
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.ideBuildNumber
import org.jetbrains.intellij.ideaDir
import org.jetbrains.intellij.info
import org.jetbrains.intellij.isDependencyOnPyCharm
import org.jetbrains.intellij.isKotlinRuntime
import org.jetbrains.intellij.isPyCharmType
import org.jetbrains.intellij.releaseType
import org.jetbrains.intellij.utils.ArchiveUtils
import org.jetbrains.intellij.utils.DependenciesDownloader
import org.jetbrains.intellij.utils.mavenRepository
import org.jetbrains.intellij.warn
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

@Suppress("BooleanMethodIsAlwaysInverted")
open class IdeaDependencyManager @Inject constructor(
    private val repositoryUrl: String,
    private val ideaDependencyCachePath: String,
    private val archiveUtils: ArchiveUtils,
    private val dependenciesDownloader: DependenciesDownloader,
    private val context: String?,
) {

    private val mainDependencies = listOf("ideaIC", "ideaIU", "riderRD", "riderRS")

    fun register(project: Project, dependency: IdeaDependency, dependencies: DependencySet) {
        val ivyFile = getOrCreateIvyXml(dependency)
        val ivyFileSuffix = ivyFile.name.substring("${dependency.name}-${dependency.version}".length).removeSuffix(".xml")

        project.repositories.ivy {
            url = dependency.classes.toURI()
            ivyPattern("${ivyFile.parent}/[module]-[revision]$ivyFileSuffix.[ext]") // ivy xml
            artifactPattern("${dependency.classes.path}/[artifact].[ext]") // idea libs
            if (dependency.sources != null) {
                artifactPattern("${dependency.sources.parent}/[artifact]-[revision]-[classifier].[ext]")
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
        "JPS" -> JpsIdeaDependency(
            version,
            buildNumber,
            classesDirectory,
            sourcesDirectory,
            !hasKotlinDependency(project),
            context,
        )
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

    private fun resolveSources(version: String, type: String): File? {
        info(context, "Adding IDE sources repository")
        try {
            val releaseType = releaseType(version)
            val forPyCharm = isPyCharmType(type)
            val sourcesFiles = dependenciesDownloader.downloadFromRepository(context, {
                create(
                    group = if (forPyCharm) "com.jetbrains.intellij.pycharm" else "com.jetbrains.intellij.idea",
                    name = if (forPyCharm) "pycharmPC" else "ideaIC",
                    version = version,
                    classifier = "sources",
                    ext = "jar",
                )
            }, {
                mavenRepository("$repositoryUrl/$releaseType")
            })
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
    ) = archiveUtils.extract(
        zipFile,
        cacheDirectory.resolve(zipFile.name.removeSuffix(".zip")),
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
            for (f in cacheDirectory.walkTopDown()) {
                if (f.isFile
                    && (f.extension == "dylib"
                            || f.extension == "py"
                            || f.extension == "sh"
                            || f.extension.startsWith("so")
                            || f.name == "dotnet"
                            || f.name == "env-wrapper"
                            || f.name == "mono-sgen"
                            || f.name == "BridgeService"
                            || f.name == "JetBrains.Profiler.PdbServer"
                            || f.name == "JBDeviceService")
                ) {
                    setExecutable(cacheDirectory, f.relativeTo(cacheDirectory).toString(), context)
                }
            }
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

                writeTo(ivyFile.toPath())
            }
        }
        return ivyFile
    }

    @Suppress("BooleanMethodIsAlwaysInverted")
    private fun hasKotlinDependency(project: Project) =
        project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).allDependencies.any {
            "org.jetbrains.kotlin" == it.group && isKotlinRuntime(it.name)
        }

    fun resolveRemote(project: Project, version: String, type: String, sources: Boolean, extraDependencies: List<String>): IdeaDependency {
        val releaseType = releaseType(version)
        debug(context, "Adding IDE repository: $repositoryUrl/$releaseType")

        debug(context, "Adding IDE dependency")
        var hasSources = sources
        val (dependencyGroup, dependencyName) = when {
            type == "IU" -> {
                "com.jetbrains.intellij.idea" to "ideaIU"
            }
            type == "IC" -> {
                "com.jetbrains.intellij.idea" to "ideaIC"
            }
            type == "CL" -> {
                "com.jetbrains.intellij.clion" to "clion"
            }
            isPyCharmType(type) -> {
                "com.jetbrains.intellij.pycharm" to "pycharm$type"
            }
            type == "GO" -> {
                "com.jetbrains.intellij.goland" to "goland"
            }
            type == "RD" -> {
                if (sources && releaseType == RELEASE_TYPE_SNAPSHOTS) {
                    warn(context, "IDE sources are not available for Rider SNAPSHOTS")
                    hasSources = false
                }
                "com.jetbrains.intellij.rider" to "riderRD"
            }
            type == "GW" -> {
                hasSources = false
                "com.jetbrains.gateway" to "JetBrainsGateway"
            }
            else -> {
                throw BuildException("Specified type '$type' is unknown. Supported values: IC, IU, CL, PY, PC, GO, RD, GW", null)
            }
        }

        val classesDirectory = dependenciesDownloader.downloadFromRepository(context, {
            create(
                group = dependencyGroup,
                name = dependencyName,
                version = version,
            )
        }, {
            mavenRepository("$repositoryUrl/$releaseType")
        }).first().let {
            debug(context, "IDE zip: " + it.path)
            unzipDependencyFile(getZipCacheDirectory(it, project, type), it, type, version.endsWith(RELEASE_SUFFIX_SNAPSHOT))
        }

        info(context, "IDE dependency cache directory: $classesDirectory")
        val buildNumber = ideBuildNumber(classesDirectory)
        val sourcesDirectory = when {
            hasSources -> resolveSources(version, type)
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

    private fun getZipCacheDirectory(zipFile: File, project: Project, type: String): File {
        if (ideaDependencyCachePath.isNotEmpty()) {
            return File(ideaDependencyCachePath).apply {
                mkdirs()
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
            val releaseType = releaseType(version)
            val files = dependenciesDownloader.downloadFromRepository(context, {
                create(
                    group = "com.jetbrains.intellij.idea",
                    name = name,
                    version = version,
                )
            }, {
                mavenRepository("$repositoryUrl/$releaseType")
            })
            if (files.size == 1) {
                val depFile = files.first()
                return when {
                    depFile.name.endsWith(".zip") -> {
                        val cacheDirectory = getZipCacheDirectory(depFile, project, "IC")
                        debug(context, "IDE extra dependency '$name': " + cacheDirectory.path)
                        unzipDependencyFile(cacheDirectory, depFile, "IC", version.endsWith(RELEASE_SUFFIX_SNAPSHOT))
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
