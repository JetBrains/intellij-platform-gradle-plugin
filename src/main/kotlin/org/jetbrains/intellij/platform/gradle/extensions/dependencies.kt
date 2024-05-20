// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.of
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes.ArtifactType
import org.jetbrains.intellij.platform.gradle.Constants.JETBRAINS_MARKETPLACE_MAVEN_GROUP
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.Constants.VERSION_CURRENT
import org.jetbrains.intellij.platform.gradle.Constants.VERSION_LATEST
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.providers.AndroidStudioDownloadLinkValueSource
import org.jetbrains.intellij.platform.gradle.providers.ModuleDescriptorsValueSource
import org.jetbrains.intellij.platform.gradle.resolvers.closestVersion.JavaCompilerClosestVersionResolver
import org.jetbrains.intellij.platform.gradle.resolvers.closestVersion.TestFrameworkClosestVersionResolver
import org.jetbrains.intellij.platform.gradle.resolvers.latestVersion.IntelliJPluginVerifierLatestVersionResolver
import org.jetbrains.intellij.platform.gradle.resolvers.latestVersion.MarketplaceZipSignerLatestVersionResolver
import org.jetbrains.intellij.platform.gradle.tasks.SignPluginTask
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.resolve
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.math.absoluteValue

/**
 * Adds a dependency on a Marketplace ZIP Signer required for signing plugin with [SignPluginTask].
 *
 * If [version] is set to [VERSION_LATEST], it resolves the latest available library version using the [MarketplaceZipSignerLatestVersionResolver].
 *
 * @param version Marketplace ZIP Signer version
 */
internal fun DependencyHandler.createMarketplaceZipSignerDependency(version: String) = create(
    group = "org.jetbrains",
    name = "marketplace-zip-signer",
    version = when (version) {
        VERSION_LATEST -> MarketplaceZipSignerLatestVersionResolver().resolve().version
        else -> version
    },
    classifier = "cli",
    ext = "jar",
)

/**
 * Creates a dependency on the `test-framework` library required for testing plugins.
 *
 * If [version] is set to [VERSION_CURRENT], it uses the [ProductInfo.buildNumber].
 *
 * If [BuildFeature.USE_CLOSEST_VERSION_RESOLVING] is enabled and [VERSION_CURRENT] is set as a version, it resolves the closest matching library version
 * using repositories currently added to the project.
 *
 * @param type Test Framework type
 * @param version Test Framework version to resolve
 * @param layout Gradle [ProjectLayout] instance
 * @param platformPath Path to the IntelliJ Platform
 * @param providers Gradle [ProviderFactory] instance
 * @param repositories Gradle project [RepositoryHandler]
 * @param settingsRepositories Gradle settings [RepositoryHandler]
 */
internal fun DependencyHandler.createTestFrameworkDependency(
    type: TestFrameworkType,
    version: String,
    layout: ProjectLayout,
    platformPath: Path,
    productInfo: ProductInfo,
    providers: ProviderFactory,
    repositories: RepositoryHandler,
    settingsRepositories: RepositoryHandler,
): Dependency {
    val resolveClosest = BuildFeature.USE_CLOSEST_VERSION_RESOLVING.getValue(providers)

    val moduleDescriptors = providers.of(ModuleDescriptorsValueSource::class) {
        parameters {
            intellijPlatformPath = layout.dir(providers.provider { platformPath.toFile() })
        }
    }

    return create(
        group = type.coordinates.groupId,
        name = type.coordinates.artifactId,
        version = when (version) {
            VERSION_CURRENT -> when {
                resolveClosest.get() ->
                    TestFrameworkClosestVersionResolver(productInfo, repositories.urls() + settingsRepositories.urls(), type.coordinates)
                        .resolve()
                        .version

                else -> productInfo.buildNumber
            }

            else -> version
        }
    ).apply {
        moduleDescriptors.get().forEach {
            exclude(it.groupId, it.artifactId)
        }
    }
}

internal fun DependencyHandler.createJavaCompilerDependency(
    version: String,
    productInfo: ProductInfo,
    providers: ProviderFactory,
    repositories: RepositoryHandler,
    settingsRepositories: RepositoryHandler,
): Dependency {
    val resolveClosest = BuildFeature.USE_CLOSEST_VERSION_RESOLVING.getValue(providers)

    return create(
        group = "com.jetbrains.intellij.java",
        name = "java-compiler-ant-tasks",
        version = when (version) {
            VERSION_CURRENT -> when {
                resolveClosest.get() ->
                    JavaCompilerClosestVersionResolver(productInfo, repositories.urls() + settingsRepositories.urls())
                        .resolve()
                        .version

                else -> productInfo.buildNumber
            }

            else -> version
        },
    )
}

internal fun DependencyHandler.createJetBrainsRuntimeDependency(
    version: String,
) = create(
    group = "com.jetbrains",
    name = "jbr",
    version = version,
    ext = "tar.gz",
)

internal fun DependencyHandler.createPluginVerifierDependency(
    version: String,
) = create(
    group = "org.jetbrains.intellij.plugins",
    name = "verifier-cli",
    version = when (version) {
        VERSION_LATEST -> IntelliJPluginVerifierLatestVersionResolver().resolve().version
        else -> version
    },
    classifier = "all",
    ext = "jar",
)

/**
 * Creates a [Dependency] using a Jar file resolved in [platformPath] with [path].
 *
 * @param path relative path to the library, like: `lib/testFramework.jar`
 * @param objects Gradle [ObjectFactory] instance
 * @param platformPath IntelliJ Platform path
 */
internal fun DependencyHandler.createBundledLibraryDependency(
    path: String,
    objects: ObjectFactory,
    platformPath: Path,
) = create(objects.fileCollection().from(platformPath.resolve(path)))

/**
 * Creates Android Studio dependency.
 *
 * @param version Android Studio version
 * @param providers Gradle [ProviderFactory] instance
 * @param resources Gradle [ResourceHandler] instance
 */
internal fun DependencyHandler.createAndroidStudioDependency(
    version: String,
    providers: ProviderFactory,
    resources: ResourceHandler,
): Dependency {
    val type = IntelliJPlatformType.AndroidStudio
    val downloadLink = providers.of(AndroidStudioDownloadLinkValueSource::class) {
        parameters {
            androidStudio = resources.resolve(Locations.PRODUCTS_RELEASES_ANDROID_STUDIO)
            androidStudioVersion = version
        }
    }.orNull

    requireNotNull(downloadLink) { "Couldn't resolve Android Studio download URL for version: $version" }
    requireNotNull(type.binary) { "Specified type '$type' has no artifact coordinates available." }

    val (classifier, extension) = downloadLink.substringAfter("$version-").split(".", limit = 2)

    return create(
        group = type.binary.groupId,
        name = type.binary.artifactId,
        classifier = classifier,
        ext = extension,
        version = version,
    )
}

/**
 * Creates IntelliJ Platform dependency based on the provided version and type.
 *
 * @param version IntelliJ Platform version
 * @param type IntelliJ Platform type
 * @param providers Gradle [ProviderFactory] instance
 */
internal fun DependencyHandler.createIntelliJPlatformDependency(
    version: String,
    type: IntelliJPlatformType,
    providers: ProviderFactory,
) = when (BuildFeature.USE_BINARY_RELEASES.isEnabled(providers).get()) {
    true -> {
        val (extension, classifier) = with(OperatingSystem.current()) {
            val arch = System.getProperty("os.arch").takeIf { it == "aarch64" }
            when {
                isWindows -> ArtifactType.ZIP to "win"
                isLinux -> ArtifactType.TAR_GZ to arch
                isMacOsX -> ArtifactType.DMG to arch
                else -> throw GradleException("Unsupported operating system: $name")
            }
        }.let { (type, classifier) -> type.toString() to classifier }

        requireNotNull(type.binary) { "Specified type '$type' has no artifact coordinates available." }

        create(
            group = type.binary.groupId,
            name = type.binary.artifactId,
            version = version,
            ext = extension,
            classifier = classifier,
        )
    }

    false -> {
        requireNotNull(type.maven) { "Specified type '$type' has no artifact coordinates available." }

        create(
            group = type.maven.groupId,
            name = type.maven.artifactId,
            version = version,
        )
    }
}

/**
 * Creates a dependency on a local IntelliJ Platform instance.
 *
 * @param localPath Path to the local IntelliJ Platform
 * @param providers Gradle [ProviderFactory] instance
 * @param rootProjectDirectory Path to the root module of the current project
 */
internal fun DependencyHandler.createLocalIntelliJPlatformDependency(
    localPath: Any,
    providers: ProviderFactory,
    rootProjectDirectory: Path,
): Dependency {
    val artifactPath = resolveArtifactPath(localPath)
    val localProductInfo = artifactPath.productInfo()

    localProductInfo.validateSupportedVersion()

    val hash = artifactPath.hashCode().absoluteValue % 1000
    val type = localProductInfo.productCode.toIntelliJPlatformType()
    val coordinates = type.maven ?: type.binary
    requireNotNull(coordinates) { "Specified type '$type' has no dependency available." }

    return create(
        group = Configurations.Dependencies.LOCAL_IDE_GROUP,
        name = coordinates.groupId,
        version = "${localProductInfo.version}+$hash",
    ).apply {
        createIvyDependencyFile(
            localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
            publications = listOf(artifactPath.toPublication()),
        )
    }
}

/**
 * Creates a dependency for an IntelliJ Platform plugin.
 *
 * @param pluginId the ID of the plugin
 * @param version the version of the plugin
 * @param channel the channel of the plugin. Can be null or empty for the default channel.
 */
internal fun DependencyHandler.createIntelliJPlatformPluginDependency(
    pluginId: String,
    version: String,
    channel: String?,
): Dependency {
    val group = when (channel) {
        "default", "", null -> JETBRAINS_MARKETPLACE_MAVEN_GROUP
        else -> "$channel.$JETBRAINS_MARKETPLACE_MAVEN_GROUP"
    }

    return create(
        group = group,
        name = pluginId.trim(),
        version = version,
    )
}

/**
 * Creates a dependency for an IntelliJ platform bundled plugin.
 *
 * @param bundledPluginId The ID of the bundled plugin
 * @param bundledPluginsList The list of available bundled plugins
 * @param productInfo The [ProductInfo] instance
 * @param providers Gradle [ProviderFactory] instance
 */
internal fun DependencyHandler.createIntelliJPlatformBundledPluginDependency(
    bundledPluginId: String,
    bundledPluginsList: BundledPlugins,
    platformPath: Path,
    productInfo: ProductInfo,
    providers: ProviderFactory,
    rootProjectDirectory: Path,
): Dependency {
    val plugin = bundledPluginsList.plugins.find { it.id == bundledPluginId }
    requireNotNull(plugin) { "Could not find bundled plugin with ID: '$bundledPluginId'" }

    return create(
        group = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
        name = plugin.id,
        version = productInfo.version,
    ).apply {
        createBundledPluginIvyDependencyFile(plugin, version!!, bundledPluginsList, platformPath, productInfo, providers, rootProjectDirectory)
    }
}

/**
 * Creates a dependency on a local IntelliJ Platform plugin.
 *
 * @param localPath Path to the local plugin
 * @param providers Gradle [ProviderFactory] instance
 * @param rootProjectDirectory Path to the root module of the current project
 */
internal fun DependencyHandler.createIntelliJPlatformLocalPluginDependency(
    localPath: Any,
    providers: ProviderFactory,
    rootProjectDirectory: Path,
): Dependency {
    val artifactPath = when (localPath) {
        is String -> localPath
        is File -> localPath.absolutePath
        is Directory -> localPath.asPath.pathString
        else -> throw IllegalArgumentException("Invalid argument type: '${localPath.javaClass}'. Supported types: String, File, or Directory.")
    }.let { Path(it) }.takeIf { it.exists() }.let { requireNotNull(it) { "Specified localPath '$localPath' doesn't exist." } }

    val plugin by lazy {
        val pluginPath = when {
            artifactPath.isDirectory() -> generateSequence(artifactPath) {
                it.takeIf { it.resolve("lib").exists() } ?: it.listDirectoryEntries().singleOrNull()
            }.firstOrNull { it.resolve("lib").exists() } ?: throw GradleException("Could not resolve plugin directory: $artifactPath")

            else -> artifactPath
        }

        val pluginCreationResult = IdePluginManager.createManager().createPlugin(pluginPath, false)

        require(pluginCreationResult is PluginCreationSuccess)
        pluginCreationResult.plugin
    }

    return create(
        group = Configurations.Dependencies.LOCAL_PLUGIN_GROUP,
        name = plugin.pluginId ?: artifactPath.name,
        version = plugin.pluginVersion ?: "0.0.0",
    ).apply {
        createIvyDependencyFile(
            localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
            publications = listOf(artifactPath.toPublication()),
        )
    }
}

// TODO: cleanup and migrate to Ivy webserver
private fun createBundledPluginIvyDependencyFile(
    bundledPlugin: BundledPlugin,
    version: String,
    bundledPluginsList: BundledPlugins,
    platformPath: Path,
    productInfo: ProductInfo,
    providers: ProviderFactory,
    rootProjectDirectory: Path,
    resolved: List<String> = emptyList(),
) {
    val localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory)
    val group = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP
    val name = bundledPlugin.id
    val existingIvyFile = localPlatformArtifactsPath.resolve("$group-$name-$version.xml")
    if (existingIvyFile.exists()) {
        return
    }

    if (name in resolved) {
        return
    }

    val artifactPath = Path(bundledPlugin.path)
    val plugin by lazy {
        val pluginCreationResult = IdePluginManager.createManager().createPlugin(artifactPath, false)
        require(pluginCreationResult is PluginCreationSuccess)
        pluginCreationResult.plugin
    }

    val dependencyIds = plugin.dependencies.map { it.id } - plugin.pluginId
    val bundledModules = productInfo.layout.filter { layout -> layout.name in dependencyIds }.filter { layout -> layout.classPath.isNotEmpty() }

    /**
     * This is a fallback method for IntelliJ Platform with no [ProductInfo.layout] data present.
     * To avoid duplications, exclude IDs of possibly resolved `bundledModules` items.
     *
     * Eventually, this data should be provided by the IntelliJ Plugin Verifier.
     */
    val fallbackBundledPlugins = run {
        val bundledModuleNames = bundledModules.map { it.name }

        bundledPluginsList.plugins.filter {
            it.id in dependencyIds && it.id !in bundledModuleNames
        }
    }

    val ivyDependencies = bundledModules.mapNotNull { layout ->
        when (layout.kind) {
            ProductInfo.LayoutItemKind.plugin -> {
                IvyModule.Dependency(
                    organization = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
                    name = layout.name,
                    version = version,
                ).also { dependency ->
                    val dependencyPlugin = bundledPluginsList.plugins.find { it.id == dependency.name } ?: return@also
                    createBundledPluginIvyDependencyFile(
                        dependencyPlugin, version, bundledPluginsList, platformPath, productInfo, providers, rootProjectDirectory, resolved + name
                    )
                }
            }

            ProductInfo.LayoutItemKind.pluginAlias -> {
                // TODO: not important?
                null
            }

            ProductInfo.LayoutItemKind.moduleV2, ProductInfo.LayoutItemKind.productModuleV2 -> {
                // TODO: drop if classPath empty?
                IvyModule.Dependency(
                    organization = Configurations.Dependencies.BUNDLED_MODULE_GROUP,
                    name = layout.name,
                    version = version,
                ).also { dependency ->
                    createIvyDependencyFile(
                        group = dependency.organization,
                        name = dependency.name,
                        version = dependency.version,
                        localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
                        publications = layout.classPath.map { classPath ->
                            platformPath.resolve(classPath).toPublication()
                        },
                    )
                }
            }
        }
    } + fallbackBundledPlugins.map { dependencyPlugin ->
        IvyModule.Dependency(
            organization = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
            name = dependencyPlugin.id,
            version = version,
        ).also {
            createBundledPluginIvyDependencyFile(
                dependencyPlugin,
                version,
                bundledPluginsList,
                platformPath,
                productInfo,
                providers,
                rootProjectDirectory,
                resolved + name
            )
        }
    }

    createIvyDependencyFile(
        group = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
        name = bundledPlugin.id,
        version = version,
        localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
        publications = listOf(artifactPath.toPublication()),
        dependencies = ivyDependencies,
    )
}
