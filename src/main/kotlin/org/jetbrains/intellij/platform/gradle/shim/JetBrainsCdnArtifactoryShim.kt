// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.shim

import io.undertow.Handlers
import io.undertow.util.Methods
import org.gradle.api.GradleException
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes.ArtifactType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.IvyModule
import org.jetbrains.intellij.platform.gradle.utils.Logger
import javax.inject.Inject

class JetBrainsCdnArtifactoryShim @Inject constructor(port: Int) : Shim(port) {

    private val log = Logger(javaClass)

    private val ivyDescriptorHandler = IvyDescriptorHttpHandler { groupId, artifactId, version ->
        log.info("Resolving artifact descriptor for '$groupId:$artifactId:$version'")

        val coordinates = Coordinates(groupId, artifactId)
        val type = IntelliJPlatformType.values().find { it.maven == coordinates }
        if (type?.binary == null || type.maven == null) {
            return@IvyDescriptorHttpHandler null
        }
        log.info("Transforming ${type.maven} to ${type.binary}")

        val (extension, classifier) = with(OperatingSystem.current()) {
            val arch = System.getProperty("os.arch").takeIf { it == "aarch64" }
            when {
                isWindows -> ArtifactType.ZIP to "win"
                isLinux -> ArtifactType.TAR_GZ to arch
                isMacOsX -> ArtifactType.DMG to arch
                else -> throw GradleException("Unsupported operating system: $name")
            }
        }.let { (type, classifier) -> type.toString() to classifier }
        log.info("extension: $extension, classifier: $classifier")

        IvyModule(
            info = IvyModule.Info(
                organisation = groupId,
                module = artifactId,
                revision = version,
            ),
            dependencies = listOf(
                IvyModule.Dependency(
                    organization = type.binary.groupId,
                    name = type.binary.artifactId,
                    version = version,
                    artifacts = listOf(
                        IvyModule.Dependency.Artifact(
                            name = type.binary.artifactId,
                            type = extension,
                            extension = extension,
                            classifier = classifier,
                        ),
                    ),
                ),
            ),
        )
    }

    override fun getRoutingHandler() =
        Handlers.routing()
            .add(Methods.HEAD, DESCRIPTOR_PATH, ivyDescriptorHandler)
            .add(Methods.GET, DESCRIPTOR_PATH, ivyDescriptorHandler)
}
