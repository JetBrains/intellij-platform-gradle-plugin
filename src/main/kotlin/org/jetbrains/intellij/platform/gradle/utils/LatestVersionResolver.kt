// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations
import org.jetbrains.intellij.platform.gradle.model.MavenMetadata
import org.jetbrains.intellij.platform.gradle.model.XmlExtractor
import java.net.HttpURLConnection
import java.net.URL

@Suppress("SameParameterValue")
class LatestVersionResolver {

    companion object {
        private val log = Logger(LatestVersionResolver::class.java)

        fun pluginVerifier() = fromMaven(
            "IntelliJ Plugin Verifier",
            "${Locations.MAVEN_REPOSITORY}/org/jetbrains/intellij/plugins/verifier-cli/maven-metadata.xml",
        )

        fun robotServerPlugin() = fromMaven(
            "Robot Server Plugin",
            "${Locations.INTELLIJ_DEPENDENCIES_REPOSITORY}/com/intellij/remoterobot/robot-server-plugin/maven-metadata.xml",
        )

        fun zipSigner() = fromMaven(
            "Marketplace ZIP Signer",
            "${Locations.MAVEN_REPOSITORY}/org/jetbrains/marketplace-zip-signer/maven-metadata.xml",
        )

        // TODO: use when 2.0 published to GPP
//        fun plugin() = fromMaven(
//            "IntelliJ Platform Gradle Plugin",
//            "${Locations.MAVEN_GRADLE_PLUGIN_PORTAL_REPOSITORY}/org/jetbrains/intellij/platform/intellij-platform-gradle-plugin/maven-metadata.xml",
//        )
        fun plugin() = fromGitHub(IntelliJPluginConstants.PLUGIN_NAME, Locations.GITHUB_REPOSITORY)

        fun closestJavaCompiler(version: String) = closestInMaven(
            "Java Compiler",
            "${Locations.INTELLIJ_REPOSITORY}/releases/com/jetbrains/intellij/java/java-compiler-ant-tasks/maven-metadata.xml",
            version.toVersion(),
        )

        private fun fromMaven(subject: String, url: String): String {
            log.debug(message = "Resolving the latest $subject version")
            return URL(url).openStream().use { inputStream ->
                XmlExtractor<MavenMetadata>().unmarshal(inputStream).versioning?.latest
                    ?: throw GradleException("Cannot resolve the latest $subject version")
            }
        }

        private fun closestInMaven(subject: String, url: String, version: Version): String {
            log.debug(message = "Resolving the $subject version closest to $version")
            return URL(url).openStream().use { inputStream ->
                XmlExtractor<MavenMetadata>().unmarshal(inputStream)
                    .versioning
                    ?.versions
                    .throwIfNull { GradleException("Cannot resolve the $subject version closest to $version") }
                    .map { it.toVersion() }
                    .filter { it <= version }
                    .maxOf { it }
                    .version
            }
        }

        private fun fromGitHub(subject: String, url: String): String {
            log.debug(message = "Resolving the latest $subject version")
            try {
                return URL("$url/releases/latest").openConnection().run {
                    (this as HttpURLConnection).instanceFollowRedirects = false
                    getHeaderField("Location").split('/').last().removePrefix("v")
                }
            } catch (e: Exception) {
                throw GradleException("Cannot resolve the latest $subject version", e)
            }
        }
    }
}
