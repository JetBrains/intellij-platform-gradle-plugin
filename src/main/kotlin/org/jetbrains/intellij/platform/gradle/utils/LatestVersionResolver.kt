// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations
import org.jetbrains.intellij.platform.gradle.debug
import org.jetbrains.intellij.platform.gradle.model.MavenMetadata
import org.jetbrains.intellij.platform.gradle.model.XmlExtractor
import java.net.HttpURLConnection
import java.net.URL

class LatestVersionResolver {

    companion object {
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

        fun fromMaven(subject: String, url: String): String {
            debug(message = "Resolving latest $subject version")
            return URL(url).openStream().use {
                XmlExtractor<MavenMetadata>().unmarshal(it).versioning?.latest
                    ?: throw GradleException("Cannot resolve the latest $subject version")
            }
        }

        fun fromGitHub(subject: String, url: String): String {
            debug(message = "Resolving latest $subject version")
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
