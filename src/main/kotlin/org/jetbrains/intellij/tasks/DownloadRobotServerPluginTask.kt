package org.jetbrains.intellij.tasks

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.IntelliJPluginConstants.INTELLIJ_DEPENDENCIES
import org.jetbrains.intellij.IntelliJPluginConstants.VERSION_LATEST
import org.jetbrains.intellij.Version
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.model.SpacePackagesMavenMetadata
import org.jetbrains.intellij.model.XmlExtractor
import org.jetbrains.intellij.utils.ArchiveUtils
import java.io.File
import java.net.URL
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class DownloadRobotServerPluginTask @Inject constructor(objectFactory: ObjectFactory) : ConventionTask() {

    companion object {
        private const val METADATA_URL = "$INTELLIJ_DEPENDENCIES/com/intellij/remoterobot/robot-server-plugin/maven-metadata.xml"
        const val OLD_ROBOT_SERVER_DEPENDENCY = "org.jetbrains.test:robot-server-plugin"
        const val NEW_ROBOT_SERVER_DEPENDENCY = "com.intellij.remoterobot:robot-server-plugin"

        fun resolveLatestVersion(): String {
            debug(message = "Resolving latest Robot Server Plugin version")
            val url = URL(METADATA_URL)
            return XmlExtractor<SpacePackagesMavenMetadata>().unmarshal(url.openStream()).versioning?.latest
                ?: throw GradleException("Cannot resolve the latest Robot Server Plugin version")
        }

        /**
         * Resolves Plugin Verifier version.
         * If set to {@link IntelliJPluginConstants#VERSION_LATEST}, there's request to {@link #VERIFIER_METADATA_URL}
         * performed for the latest available verifier version.
         *
         * @return Plugin Verifier version
         */
        fun resolveVersion(version: String?) = version?.takeIf { it != VERSION_LATEST } ?: resolveLatestVersion()

        fun getDependency(version: String) = when {
            Version.parse(version) < Version.parse("0.11.0") -> OLD_ROBOT_SERVER_DEPENDENCY
            else -> NEW_ROBOT_SERVER_DEPENDENCY
        }
    }

    @Input
    val version: Property<String> = objectFactory.property(String::class.java)

    @InputFile
    val pluginArchive: Property<File> = objectFactory.property(File::class.java)

    @OutputDirectory
    val outputDir: DirectoryProperty = objectFactory.directoryProperty()

    private val archiveUtils = objectFactory.newInstance(ArchiveUtils::class.java)

    private val context = logCategory()

    @TaskAction
    fun downloadPlugin() {
        val target = outputDir.get().asFile
        archiveUtils.extract(pluginArchive.get(), target, context)
    }
}
