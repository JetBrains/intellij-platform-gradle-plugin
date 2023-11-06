// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.dependency

import com.jetbrains.plugin.structure.base.utils.*
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.*
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.RELEASE_SUFFIX_SNAPSHOT
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.utils.ArchiveUtils
import org.jetbrains.intellij.platform.gradle.utils.DependenciesDownloader
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import javax.inject.Inject

@Suppress("BooleanMethodIsAlwaysInverted")
abstract class IdeaDependencyManager @Inject constructor(
    private val ideaDependencyCachePath: String,
    private val archiveUtils: ArchiveUtils,
    private val dependenciesDownloader: DependenciesDownloader,
    private val context: String?,
) {

    private val mainDependencies = listOf("ideaIC", "ideaIU", "riderRD", "riderRS")
    private val sourceZipArtifacts = listOf("src_lsp-openapi", "src_lsp-api")

    fun register(project: Project, dependency: IdeaDependency, dependencies: DependencySet) {
        val ivyFile = getOrCreateIvyXml(dependency)
        val ivyFileSuffix = ivyFile.simpleName.substring("${dependency.name}-${dependency.version}".length).removeSuffix(".xml")

        project.repositories.ivy {
            url = dependency.classes.toURI()
            ivyPattern("${ivyFile.parent}/[module]-[revision]$ivyFileSuffix.[ext]") // ivy xml
            artifactPattern("${dependency.classes.path}/[artifact].[ext]") // idea libs
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
        type: IntelliJPlatformType?,
        version: String,
        buildNumber: String,
        classesDirectory: File,
        project: Project,
        extraDependencies: Collection<IdeaExtraDependency>,
    ) = when (type) {
//        JPS -> JpsIdeaDependency(
//            version,
//            buildNumber,
//            classesDirectory,
//            !hasKotlinDependency(project),
//            context,
//        )

        else -> {
            val pluginsRegistry = BuiltinPluginsRegistry.fromDirectory(classesDirectory.resolve("plugins").toPath(), context)
            when (type) {
                null -> LocalIdeaDependency(
                    name,
                    version,
                    buildNumber,
                    classesDirectory,
                    !hasKotlinDependency(project),
                    pluginsRegistry,
                    extraDependencies,
                )

                else -> IdeaDependency(
                    name,
                    version,
                    buildNumber,
                    classesDirectory,
                    withKotlin = !hasKotlinDependency(project),
                    pluginsRegistry = pluginsRegistry,
                    extraDependencies = extraDependencies,
                )
            }
        }
    }

    private fun unzipDependencyFile(
        cacheDirectory: File,
        zipFile: File,
        type: IntelliJPlatformType,
        checkVersionChange: Boolean,
    ) = archiveUtils.extract(
        zipFile.toPath(), // FIXME
        cacheDirectory.resolve(
            zipFile
                .name
                .removeSuffix(".zip")
                .removeSuffix(".tar.gz")
        ).toPath(), // FIXME
        context,
        { markerFile -> isCacheUpToDate(zipFile, markerFile.toFile(), checkVersionChange) }, // FIXME
        { unzippedDirectory, markerFile ->
            resetExecutablePermissions(unzippedDirectory.toFile(), type) // FIXME
            storeCache(unzippedDirectory.toFile(), markerFile.toFile()) // FIXME
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
            val entry = zip.getEntry("build.txt") ?: return false
            val entryContent = zip.getInputStream(entry).use { it.bufferedReader().readText() }
            if (entryContent != markerFile.readText()) {
                return false
            }
        }
        return true
    }

    private fun storeCache(directoryToCache: File, markerFile: File) {
        File(directoryToCache, "build.txt")
            .takeIf(File::exists)
            ?.let { markerFile.writeText(it.readText().trim()) }
    }

    private fun resetExecutablePermissions(cacheDirectory: File, type: IntelliJPlatformType) {
        if (type == Rider && !OperatingSystem.current().isWindows) {
            for (file in cacheDirectory.walkTopDown()) {
                if (file.isFile
                    && (file.extension == "dylib"
                            || file.extension == "py"
                            || file.extension == "sh"
                            || file.extension.startsWith("so")
                            || file.name == "dotnet"
                            || file.name == "env-wrapper"
                            || file.name == "mono-sgen"
                            || file.name == "BridgeService"
                            || file.name == "JetBrains.Profiler.PdbServer"
                            || file.name == "JBDeviceService"
                            || file.name == "Rider.Backend")
                ) {
                    setExecutable(cacheDirectory, file.relativeTo(cacheDirectory).toString(), context)
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

    private fun getOrCreateIvyXml(dependency: IdeaDependency): Path {
        val directory = dependency.getIvyRepositoryDirectory()?.toPath()
        val ivyFile = when {
            directory != null -> directory.resolve("${dependency.getFqn()}.xml")
            else -> Files.createTempFile(dependency.getFqn(), ".xml")
        }

        if (directory == null || !ivyFile.exists()) {
            val identity = IntelliJIvyDescriptorFileGenerator.IvyCoordinates("com.jetbrains", dependency.name, dependency.version)
            IntelliJIvyDescriptorFileGenerator(identity).apply {
                addConfiguration(DefaultIvyConfiguration("default"))
                addConfiguration(DefaultIvyConfiguration("compile"))

                dependency.jarFiles
                    .forEach { addArtifact(IntellijIvyArtifact.createJarDependency(it.toPath(), "compile", dependency.classes.toPath())) }

                dependency.sourceZipFiles
                    .filter { it.nameWithoutExtension in sourceZipArtifacts }
                    .forEach { addArtifact(IntellijIvyArtifact.createZipDependency(it.toPath(), "sources", dependency.classes.toPath())) }

                writeTo(ivyFile)
            }
        }
        return ivyFile
    }

    @Suppress("BooleanMethodIsAlwaysInverted")
    private fun hasKotlinDependency(project: Project) =
        project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            .allDependencies
            .any { "org.jetbrains.kotlin" == it.group && isKotlinRuntime(it.name) }

    @Deprecated("Use DependencyHandlerScope.intellijPlatform(type: IntelliJPlatformType, version: String, configurationName: String)")
    fun resolveRemote(project: Project, version: String, type: String, extraDependencies: List<String>): IdeaDependency {
        debug(context, "Adding IDE dependency")

        val type = IntelliJPlatformType.fromCode(type)

        val remoteIdeaDependency = when {
            type == IntellijIdeaUltimate -> RemoteIdeaDependency(
                group = IntellijIdeaUltimate.groupId,
                name = IntellijIdeaUltimate.artifactId,
            )

            type == IntellijIdeaCommunity -> RemoteIdeaDependency(
                group = IntellijIdeaCommunity.groupId,
                name = IntellijIdeaCommunity.artifactId,
            )

            type == CLion -> RemoteIdeaDependency(
                group = CLion.groupId,
                name = CLion.artifactId,
            )

            type == PyCharmProfessional -> RemoteIdeaDependency(
                group = PyCharmProfessional.groupId,
                name = PyCharmProfessional.artifactId,
            )

            type == PyCharmCommunity -> RemoteIdeaDependency(
                group = PyCharmCommunity.groupId,
                name = PyCharmCommunity.artifactId,
            )

            type == GoLand -> RemoteIdeaDependency(
                group = GoLand.groupId,
                name = GoLand.artifactId,
            )

            type == PhpStorm -> RemoteIdeaDependency(
                group = PhpStorm.groupId,
                name = PhpStorm.artifactId,
            )

            type == Rider -> RemoteIdeaDependency(
                group = Rider.groupId,
                name = Rider.artifactId,
            )

            type == Gateway -> RemoteIdeaDependency(
                group = Gateway.groupId,
                name = Gateway.artifactId,
            )

            type == AndroidStudio -> RemoteIdeaDependency(
                group = AndroidStudio.groupId,
                name = AndroidStudio.artifactId,
                artifactExtension = when {
                    OperatingSystem.current().isLinux -> "tar.gz"
                    else -> "zip"
                },
            ) {
                with(it) {
                    Files.list(resolveAndroidStudioPath(this))
                        .forEach { entry -> Files.move(entry, resolve(entry.fileName), StandardCopyOption.REPLACE_EXISTING) }
                }
            }

            type == Fleet -> RemoteIdeaDependency(
                group = Fleet.groupId,
                name = Fleet.artifactId,
            )

            else -> throw BuildException(
                "Specified type '$type' is unknown. Supported values: ${IntelliJPlatformType.values().joinToString(", ") { it.code }}",
                null
            )
        }

        val classesDirectory = dependenciesDownloader.downloadFromRepository(context, {
            create(
                group = remoteIdeaDependency.group,
                name = remoteIdeaDependency.name,
                version = version,
                ext = remoteIdeaDependency.artifactExtension,
            )
//            when (type) {
//                PLATFORM_TYPE_ANDROID_STUDIO -> {
//                    val androidStudioReleases =
//                        dependenciesDownloader.getAndroidStudioReleases(context)?.let {
//                            XmlExtractor<AndroidStudioReleases>(context).fetch(it)
//                        } ?: throw GradleException("Cannot resolve Android Studio Releases list")
//
//                    val release = androidStudioReleases.items.find {
//                        it.version == version || it.build == "$PLATFORM_TYPE_ANDROID_STUDIO-$version"
//                    } ?: throw GradleException("Cannot resolve Android Studio with provided version: $version")
//
//                    val arch = System.getProperty("os.arch")
//                    val hasAppleM1Link by lazy { release.downloads.any { it.link.contains("-mac_arm.zip") } }
//                    val suffix = with(OperatingSystem.current()) {
//                        when {
//                            isMacOsX -> when {
//                                arch == "aarch64" && hasAppleM1Link -> "-mac_arm.zip"
//                                else -> "-mac.zip"
//                            }
//
//                            isLinux -> "-linux.tar.gz"
//                            else -> "-windows.zip"
//                        }
//                    }
//                    val url = release.downloads
//                        .find { it.link.endsWith(suffix) }
//                        ?.link
//                        ?: throw GradleException("Cannot resolve Android Studio with provided version: $version")
//
//                    ivyRepository(url)
//                }
//
//                else -> null
        }).first().let {
            debug(context, "IDE zip: " + it.path)
            unzipDependencyFile(getZipCacheDirectory(it, project, type), it, type, version.endsWith(RELEASE_SUFFIX_SNAPSHOT))
                .also(remoteIdeaDependency.postProcess)
        }

        info(context, "IDE dependency cache directory: $classesDirectory")
        val buildNumber = classesDirectory.productInfo().buildNumber
        val resolvedExtraDependencies = resolveExtraDependencies(project, version, extraDependencies)
        return createDependency(
            remoteIdeaDependency.name,
            type,
            version,
            buildNumber,
            classesDirectory.toFile(), // FIXME
            project,
            resolvedExtraDependencies,
        )
    }

    fun resolveLocal(project: Project, localPath: String): IdeaDependency {
        debug(context, "Adding local IDE dependency")
        val ideaDir = Path.of(localPath).let {
            it.takeUnless { it.endsWith(".app") } ?: it.resolve("Contents")
        }

        if (!ideaDir.exists() || !ideaDir.isDirectory) {
            throw BuildException("Specified localPath '$localPath' doesn't exist or is not a directory", null)
        }
        val buildNumber = ideaDir.productInfo().buildNumber
        return createDependency("ideaLocal", null, buildNumber, buildNumber, ideaDir.toFile(), project, emptyList())
    }

    private fun getZipCacheDirectory(zipFile: File, project: Project, type: IntelliJPlatformType): File {
        if (ideaDependencyCachePath.isNotEmpty()) {
            return File(ideaDependencyCachePath).apply {
                mkdirs()
            }
        } else if (type == Rider && OperatingSystem.current().isWindows) {
            return project.layout.buildDirectory.asFile.get()
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
            val files = dependenciesDownloader.downloadFromRepository(context, {
                create(
                    group = "com.jetbrains.intellij.idea",
                    name = name,
                    version = version,
                )
            })
            if (files.size == 1) {
                val dependencyFile = files.first() // TODO: remove when migrated to Path
                val dependency = dependencyFile.toPath()

                return when {
                    dependency.isZip() -> {
                        val cacheDirectory = getZipCacheDirectory(dependencyFile, project, IntellijIdeaCommunity)
                        debug(context, "IDE extra dependency '$name': " + cacheDirectory.path)
                        unzipDependencyFile(
                            cacheDirectory,
                            dependencyFile,
                            IntellijIdeaCommunity,
                            version.endsWith(RELEASE_SUFFIX_SNAPSHOT)
                        ).toFile() // FIXME
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

    private fun resolveAndroidStudioPath(parentPath: Path) = when {
        // such as Android Studio.app/Contents
        OperatingSystem.current().isMacOsX ->
            Files.list(parentPath)
                .filter { it.extension == "app" }
                .findFirst()
                .get()
                .resolve("Contents")

        else -> parentPath.resolve("android-studio")
    }.also {
        info(context, "Android Studio path for ${OperatingSystem.current().name} resolved as: $it")
    }

    private data class RemoteIdeaDependency(
        val group: String,
        val name: String,
        val artifactExtension: String = "zip",
        val postProcess: (Path) -> Unit = {},
    )
}
