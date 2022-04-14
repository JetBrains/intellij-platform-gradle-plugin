package org.jetbrains.intellij.utils

import org.gradle.api.GradleException
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.model.MavenMetadata
import org.jetbrains.intellij.model.XmlExtractor
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class LatestVersionResolver {

    companion object {
        fun fromMaven(subject: String, url: String): String {
            debug(message = "Resolving latest $subject version")
            return XmlExtractor<MavenMetadata>().unmarshal(URL(url).openStream()).versioning?.latest
                ?: throw GradleException("Cannot resolve the latest $subject version")
        }

        fun fromGitHub(subject: String, url: String): String {
            debug(message = "Resolving latest $subject version")
            try {
                return URL("$url/releases/latest").openConnection().run {
                    (this as HttpURLConnection).instanceFollowRedirects = false
                    getHeaderField("Location").split('/').last().removePrefix("v")
                }
            } catch (e: IOException) {
                throw GradleException("Cannot resolve the latest $subject version")
            }
        }
    }
}
