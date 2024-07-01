// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.shim

import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import io.undertow.util.Methods
import io.undertow.util.PathTemplateMatch
import io.undertow.util.StatusCodes
import org.gradle.api.GradleException
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes.ArtifactType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.JetBrainsCdnArtifactsRepository
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.IvyModule
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject

class JetBrainsCdnArtifactoryShim @Inject constructor(
    private val repository: JetBrainsCdnArtifactsRepository,
    port: Int,
) : Shim(port) {

    private val log = Logger(javaClass)

    private val ivyDescriptorHandler = IvyDescriptorHttpHandler { groupId, artifactId, version ->
        val downloadUrl = resolveDownloadUrl(groupId, artifactId, version)?.toString() ?: return@IvyDescriptorHttpHandler null

        log.info("Resolved download URL: $downloadUrl")

        val extension = with(downloadUrl.substringAfterLast('/')) {
            val name = substringBeforeLast('.').removeSuffix(".tar")
            removePrefix("$name.")
        }

        IvyModule(
            info = IvyModule.Info(
                organisation = groupId,
                module = artifactId,
                revision = version,
            ),
            publications = listOf(
                IvyModule.Publication(
                    url = downloadUrl,
                    ext = extension,
                    type = extension,
                ),
            ),
        )
    }

    private val downloadHandler: (HttpServerExchange) -> Unit = { exchange ->
        val parameters = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY).parameters
        val groupId = parameters[GROUP_ID].orEmpty()
        val artifactId = parameters[ARTIFACT_ID].orEmpty()
        val version = parameters[VERSION].orEmpty()

        log.info("Resolving content for $groupId:$artifactId:$version")
        val downloadUrl = resolveDownloadUrl(groupId, artifactId, version)
        log.info("Resolved download URL: $downloadUrl")

        when (downloadUrl) {
            null -> {
                exchange.statusCode = StatusCodes.NOT_FOUND
            }

            else -> {
                exchange.statusCode = StatusCodes.FOUND
                exchange.responseHeaders.put(Headers.LOCATION, downloadUrl.toString())
            }
        }
    }

    override fun getRoutingHandler() =
        Handlers.routing()
            .add(Methods.HEAD, DESCRIPTOR_PATH, ivyDescriptorHandler)
            .add(Methods.GET, DESCRIPTOR_PATH, ivyDescriptorHandler)
            .add(Methods.HEAD, DOWNLOAD_PATH, downloadHandler)
            .add(Methods.GET, DOWNLOAD_PATH, downloadHandler)

    private fun resolveDownloadUrl(groupId: String, artifactId: String, version: String): URI? {
        val coordinates = Coordinates(groupId, artifactId)
        val type = IntelliJPlatformType.values().find { it.maven == coordinates || it.binary == coordinates } ?: return null
        if (type.binary == null) {
            return null
        }

        val (extension, classifier) = with(OperatingSystem.current()) {
            val arch = System.getProperty("os.arch").takeIf { it == "aarch64" }
            when {
                isWindows -> ArtifactType.ZIP to "win"
                isLinux -> ArtifactType.TAR_GZ to arch
                isMacOsX -> ArtifactType.DMG to arch
                else -> throw GradleException("Unsupported operating system: $name")
            }
        }.let { (type, classifier) -> type.toString() to classifier }

        val downloadUrl = with(repository.url) {
            listOf(
                "/${type.binary.groupId}/${type.binary.artifactId}-$version-$classifier.$extension",
                "/${type.binary.groupId}/${type.binary.artifactId}-$version.$classifier.$extension",
                "/${type.binary.groupId}/${type.binary.artifactId}-$version.$extension",
                "/${type.binary.groupId}/$version/${type.binary.artifactId}-$version-$classifier.$extension",
                "/${type.binary.groupId}/$version/${type.binary.artifactId}-$version.$classifier.$extension",
                "/${type.binary.groupId}/$version/${type.binary.artifactId}-$version.$extension",
            )
                .map { URI(scheme, userInfo, host, port, it, null, null) }
                .find {
                    val connection = it.toURL().openConnection() as HttpURLConnection

                    with(connection) {
                        instanceFollowRedirects = true
                        runCatching {
                            inputStream.use {
                                responseCode != HttpURLConnection.HTTP_NOT_FOUND
                            }
                        }.getOrNull().also { disconnect() } ?: false
                    }
                }
        }

        return downloadUrl
    }
}
