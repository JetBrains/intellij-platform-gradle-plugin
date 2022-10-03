// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.base.utils.isZip
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
import org.jetbrains.intellij.*
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPES
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_ANDROID_STUDIO
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_CLION
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_GATEWAY
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_GOLAND
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_INTELLIJ_COMMUNITY
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_INTELLIJ_ULTIMATE
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_PHPSTORM
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_RIDER
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_SUFFIX_SNAPSHOT
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_TYPE_SNAPSHOTS
import org.jetbrains.intellij.model.AndroidStudioReleases
import org.jetbrains.intellij.model.XmlExtractor
import org.jetbrains.intellij.utils.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import javax.inject.Inject

@Suppress("BooleanMethodIsAlwaysInverted")
internal abstract class IdeaDependencyManager @Inject constructor(
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

        dependencies.add(
            project.dependencies.create(
                group = "com.jetbrains",
                name = dependency.name,
                version = dependency.version,
                configuration = "compile",
            )
        )
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
        cacheDirectory.resolve(zipFile.name.removeSuffix(".zip").removeSuffix(".tar.gz")),
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
            if (entry != null && zip.getInputStream(entry).use { it.bufferedReader().readText() } != markerFile.readText()) {
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
        if (type == PLATFORM_TYPE_RIDER && !OperatingSystem.current().isWindows) {
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
                            || f.name == "JBDeviceService"
                            || f.name == "Rider.Backend")
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

                writeTo(ivyFile)
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

        val remoteIdeaDependency = when {
            type == PLATFORM_TYPE_INTELLIJ_ULTIMATE -> RemoteIdeaDependency(
                "com.jetbrains.intellij.idea",
                "ideaIU"
            )

            type == PLATFORM_TYPE_INTELLIJ_COMMUNITY -> RemoteIdeaDependency(
                "com.jetbrains.intellij.idea",
                "ideaIC"
            )

            type == PLATFORM_TYPE_CLION -> RemoteIdeaDependency("com.jetbrains.intellij.clion", "clion")

            isPyCharmType(type) -> RemoteIdeaDependency("com.jetbrains.intellij.pycharm", "pycharm$type")

            type == PLATFORM_TYPE_GOLAND -> RemoteIdeaDependency("com.jetbrains.intellij.goland", "goland")

            type == PLATFORM_TYPE_PHPSTORM -> RemoteIdeaDependency("com.jetbrains.intellij.phpstorm", "phpstorm")

            type == PLATFORM_TYPE_RIDER ->
                RemoteIdeaDependency("com.jetbrains.intellij.rider", "riderRD", hasSources = run {
                    false.takeIf { sources && releaseType == RELEASE_TYPE_SNAPSHOTS }?.also {
                        warn(context, "IDE sources are not available for Rider SNAPSHOTS")
                    }
                })

            type == PLATFORM_TYPE_GATEWAY -> RemoteIdeaDependency(
                "com.jetbrains.gateway",
                "JetBrainsGateway",
                hasSources = false
            )

            type == PLATFORM_TYPE_ANDROID_STUDIO -> RemoteIdeaDependency(
                "com.google.android.studio",
                "android-studio",
                hasSources = false,
                artifactExtension = "tar.gz"
            ) {
                with(it.toPath()) {
                    Files.list(resolve("android-studio")).forEach { entry ->
                        Files.move(entry, resolve(entry.fileName), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }

            else -> {
                throw BuildException("Specified type '$type' is unknown. Supported values: ${PLATFORM_TYPES.joinToString()}", null)
            }
        }

        val classesDirectory = dependenciesDownloader.downloadFromRepository(context, {
            create(
                group = remoteIdeaDependency.group,
                name = remoteIdeaDependency.name,
                version = version,
                ext = remoteIdeaDependency.artifactExtension,
            )
        }, {
            when (type) {
                PLATFORM_TYPE_ANDROID_STUDIO -> {
                    val androidStudioReleases = XmlExtractor<AndroidStudioReleases>(context)
                        .fetch(dependenciesDownloader.getAndroidStudioReleases(context))
                        ?: throw GradleException("Cannot resolve Android Studio Releases list")

                    val release = androidStudioReleases.items.find {
                        it.version == version || it.build == "$PLATFORM_TYPE_ANDROID_STUDIO-$version"
                    } ?: throw GradleException("Cannot resolve Android Studio with provided version: $version")

                    val url = release.downloads.find {
                        it.link.endsWith("-linux.tar.gz")
                    }?.link ?: throw GradleException("Cannot resolve Android Studio with provided version: $version")

                    ivyRepository(url)
                }

                else -> {
                    mavenRepository("$repositoryUrl/$releaseType")
                }
            }
        }).first().let {
            debug(context, "IDE zip: " + it.path)
            unzipDependencyFile(getZipCacheDirectory(it, project, type), it, type, version.endsWith(RELEASE_SUFFIX_SNAPSHOT))
                .also(remoteIdeaDependency.postProcess)
        }

        info(context, "IDE dependency cache directory: $classesDirectory")
        val buildNumber = ideBuildNumber(classesDirectory)
        val sourcesDirectory = when {
            remoteIdeaDependency.hasSources ?: sources -> resolveSources(version, type)
            else -> null
        }
        val resolvedExtraDependencies = resolveExtraDependencies(project, version, extraDependencies)
        return createDependency(
            remoteIdeaDependency.name,
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
        } else if (type == PLATFORM_TYPE_RIDER && OperatingSystem.current().isWindows) {
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
                val dependencyFile = files.first() // TODO: remove when migrated to Path
                val dependency = dependencyFile.toPath()

                return when {
                    dependency.isZip() -> {
                        val cacheDirectory = getZipCacheDirectory(dependencyFile, project, PLATFORM_TYPE_INTELLIJ_COMMUNITY)
                        debug(context, "IDE extra dependency '$name': " + cacheDirectory.path)
                        unzipDependencyFile(
                            cacheDirectory,
                            dependencyFile,
                            PLATFORM_TYPE_INTELLIJ_COMMUNITY,
                            version.endsWith(RELEASE_SUFFIX_SNAPSHOT)
                        )
                    }

                    else -> {
                        debug(context, "IDE extra dependency '$name': " + dependencyFile.path)
                        dependencyFile
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

    private data class RemoteIdeaDependency(
        val group: String,
        val name: String,
        val hasSources: Boolean? = null,
        val artifactExtension: String = "zip",
        val postProcess: (File) -> Unit = {},
    )
}
