// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.aware

import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes.ArtifactType
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.extensions.DependencyAction
import org.jetbrains.intellij.platform.gradle.extensions.createIvyDependencyFile
import org.jetbrains.intellij.platform.gradle.extensions.localPlatformArtifactsPath
import org.jetbrains.intellij.platform.gradle.extensions.resolveArtifactPath
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.models.toPublication
import org.jetbrains.intellij.platform.gradle.models.validateSupportedVersion
import org.jetbrains.intellij.platform.gradle.providers.AndroidStudioDownloadLinkValueSource
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.resolve
import java.io.File
import java.nio.file.Path
import kotlin.math.absoluteValue

interface IntelliJPlatformDependencyAware : DependencyAware {
    val providers: ProviderFactory
    val resources: ResourceHandler
    val rootProjectDirectory: Path
}

/**
 * A base method for adding a dependency on IntelliJ Platform.
 *
 * @param typeProvider The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
 * @param versionProvider The provider for the version of the IntelliJ Platform dependency.
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action An optional action to be performed on the created dependency.
 * @throws GradleException
 */
@Throws(GradleException::class)
internal fun IntelliJPlatformDependencyAware.addIntelliJPlatformDependency(
    typeProvider: Provider<*>,
    versionProvider: Provider<String>,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addLater(
    typeProvider.map { it.toIntelliJPlatformType() }.zip(versionProvider) { type, version ->
        when (type) {
            IntelliJPlatformType.AndroidStudio -> {
                val downloadLink = providers.of(AndroidStudioDownloadLinkValueSource::class) {
                    parameters {
                        androidStudio = resources.resolve(Locations.PRODUCTS_RELEASES_ANDROID_STUDIO)
                        androidStudioVersion = version
                    }
                }.orNull

                requireNotNull(downloadLink) { "Couldn't resolve Android Studio download URL for version: $version" }
                requireNotNull(type.binary) { "Specified type '$type' has no artifact coordinates available." }

                val (classifier, extension) = downloadLink.substringAfter("$version-").split(".", limit = 2)

                dependencies.create(
                    group = type.binary.groupId,
                    name = type.binary.artifactId,
                    classifier = classifier,
                    ext = extension,
                    version = version,
                )
            }

            else -> when (BuildFeature.USE_BINARY_RELEASES.isEnabled(providers).get()) {
                true -> {
                    val extension = with(OperatingSystem.current()) {
                        when {
                            isWindows -> ArtifactType.ZIP
                            isLinux -> ArtifactType.TAR_GZ
                            isMacOsX -> ArtifactType.DMG
                            else -> throw GradleException("Unsupported operating system: $name")
                        }.toString()
                    }

                    val archClassifier = System.getProperty("os.arch")
                        .takeIf { OperatingSystem.current().isMacOsX && it == "aarch64" }

                    requireNotNull(type.binary) { "Specified type '$type' has no artifact coordinates available." }

                    dependencies.create(
                        group = type.binary.groupId,
                        name = type.binary.artifactId,
                        version = version,
                        ext = extension,
                        classifier = archClassifier,
                    )
                }

                false -> {
                    requireNotNull(type.maven) { "Specified type '$type' has no artifact coordinates available." }

                    dependencies.create(
                        group = type.maven.groupId,
                        name = type.maven.artifactId,
                        version = version,
                    )
                }
            }
        }.apply(action)
    },
)

/**
 * A base method for adding a dependency on a local IntelliJ Platform instance.
 *
 * @param localPathProvider The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action An optional action to be performed on the created dependency.
 * @throws GradleException
 */
@Throws(GradleException::class)
internal fun IntelliJPlatformDependencyAware.addIntelliJPlatformLocalDependency(
    localPathProvider: Provider<*>,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_LOCAL,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addLater(
    localPathProvider.map { localPath ->
        val artifactPath = resolveArtifactPath(localPath)
        val localProductInfo = artifactPath.productInfo()

        localProductInfo.validateSupportedVersion()

        val hash = artifactPath.hashCode().absoluteValue % 1000
        val type = localProductInfo.productCode.toIntelliJPlatformType()
        val coordinates = type.maven ?: type.binary
        requireNotNull(coordinates) { "Specified type '$type' has no dependency available." }

        dependencies.create(
            group = Configurations.Dependencies.LOCAL_IDE_GROUP,
            name = coordinates.groupId,
            version = "${localProductInfo.version}+$hash",
        ).apply {
            createIvyDependencyFile(
                localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
                publications = listOf(artifactPath.toPublication()),
            )
        }.apply(action)
    },
)
