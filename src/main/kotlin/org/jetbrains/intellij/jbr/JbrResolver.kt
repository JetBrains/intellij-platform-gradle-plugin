package org.jetbrains.intellij.jbr

import de.undercouch.gradle.tasks.download.DownloadAction
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations
import org.gradle.util.VersionNumber
import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.untar
import org.jetbrains.intellij.warn
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class JbrResolver @Inject constructor(
    val project: Project,
    val task: Task?,
    private val jreRepository: String?,
    private val archiveOperations: ArchiveOperations,
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) {

    @Transient
    private val context = task ?: project

    private val cacheDirectoryPath = Paths.get(
        project.gradle.gradleUserHomeDir.absolutePath,
        "caches/modules-2/files-2.1/com.jetbrains/jbre",
    ).toString()
    private val operatingSystem = OperatingSystem.current()

    fun resolve(version: String?): Jbr? {
        if (version.isNullOrEmpty()) {
            return null
        }
        val jbrArtifact = JbrArtifact.from(
            ("8".takeIf { version.startsWith('u') } ?: "") + version,
            operatingSystem,
        )
        val javaDir = File(cacheDirectoryPath, jbrArtifact.name)
        if (javaDir.exists()) {
            if (javaDir.isDirectory) {
                return fromDir(javaDir, version)
            }
            javaDir.delete()
        }

        getJavaArchive(jbrArtifact)?.let {
            untar(it, javaDir, archiveOperations, execOperations, fileSystemOperations, context)
            it.delete()
            return fromDir(javaDir, version)
        }
        return null
    }

    private fun fromDir(javaDir: File, version: String): Jbr? {
        val javaExecutable = findJavaExecutable(javaDir)
        if (javaExecutable == null) {
            warn(context, "Cannot find java executable in $javaDir")
            return null
        }
        return Jbr(version, javaDir, findJavaExecutable(javaDir))
    }

    private fun getJavaArchive(jbrArtifact: JbrArtifact): File? {
        val artifactName = jbrArtifact.name
        val archiveName = "${artifactName}.tar.gz"
        val javaArchive = File(cacheDirectoryPath, archiveName)
        if (javaArchive.exists()) {
            return javaArchive
        }

        if (project.gradle.startParameter.isOffline) {
            warn(context, "Cannot download JetBrains Java Runtime $artifactName. Gradle runs in offline mode.")
            return null
        }

        val url = "${jreRepository ?: jbrArtifact.repositoryUrl}/$archiveName"
        return try {
            DownloadAction(project).apply {
                src(url)
                dest(javaArchive.absolutePath)
                tempAndMove(true)
                execute()
            }
            javaArchive
        } catch (e: IOException) {
            warn(context, "Cannot download JetBrains Java Runtime $artifactName", e)
            null
        }
    }

    private fun findJavaExecutable(javaHome: File): String? {
        val root = getJbrRoot(javaHome)
        val jre = File(root, "jre")
        val java = File(
            jre.takeIf { it.exists() } ?: root,
            "bin/java" + (".exe".takeIf { operatingSystem.isWindows } ?: "")
        )
        return java.absolutePath.takeIf { java.exists() }
    }

    private fun getJbrRoot(javaHome: File): File {
        val jbr = javaHome.listFiles()?.first { it.name == "jbr" || it.name == "jbrsdk" }
        if (jbr != null && jbr.exists()) {
            return when (operatingSystem.isMacOsX) {
                true -> File(jbr, "Contents/Home")
                false -> jbr
            }
        }
        return File(javaHome, when (operatingSystem.isMacOsX) {
            true -> "jdk/Contents/Home"
            false -> ""
        })
    }

    private class JbrArtifact(val name: String, val repositoryUrl: String) {

        companion object {
            fun from(version: String, operatingSystem: OperatingSystem): JbrArtifact {
                var prefix = getPrefix(version)
                val lastIndexOfB = version.lastIndexOf('b')
                val majorVersion = when (lastIndexOfB > -1) {
                    true -> version.substring(prefix.length, lastIndexOfB)
                    false -> version.substring(prefix.length)
                }
                val buildNumberString = when (lastIndexOfB > -1) {
                    true -> version.substring(lastIndexOfB + 1)
                    else -> ""
                }
                val buildNumber = VersionNumber.parse(buildNumberString)
                val isJava8 = majorVersion.startsWith('8')
                val repositoryUrl = IntelliJPluginConstants.DEFAULT_JBR_REPO

                val oldFormat = prefix == "jbrex" || isJava8 && buildNumber < VersionNumber.parse("1483.24")
                if (oldFormat) {
                    return JbrArtifact("jbrex${majorVersion}b${buildNumberString}_${platform(operatingSystem)}_${arch(false)}",
                        repositoryUrl)
                }

                if (prefix.isEmpty()) {
                    prefix = when {
                        isJava8 -> "jbrx-"
                        buildNumber < VersionNumber.parse("1319.6") -> "jbr-"
                        else -> "jbr_jcef-"
                    }
                }
                return JbrArtifact("$prefix${majorVersion}-${platform(operatingSystem)}-${arch(isJava8)}-b${buildNumberString}",
                    repositoryUrl)
            }

            private fun getPrefix(version: String) = when {
                version.startsWith("jbrsdk-") -> "jbrsdk-"
                version.startsWith("jbr_jcef-") -> "jbr_jcef-"
                version.startsWith("jbr-") -> "jbr-"
                version.startsWith("jbrx-") -> "jbrx-"
                version.startsWith("jbrex8") -> "jbrex"
                else -> ""
            }

            private fun platform(operatingSystem: OperatingSystem) = when {
                operatingSystem.isWindows -> "windows"
                operatingSystem.isMacOsX -> "osx"
                else -> "linux"
            }

            private fun arch(newFormat: Boolean): String {
                val arch = System.getProperty("os.arch")
                if ("aarch64" == arch || "arm64" == arch) {
                    return "aarch64"
                }
                if ("x86_64" == arch || "amd64" == arch) {
                    return "x64"
                }
                val name = System.getProperty("os.name")
                if (name.contains("Windows") && System.getenv("ProgramFiles(x86)") != null) {
                    return "x64"
                }
                return when (newFormat) {
                    true -> "i586"
                    false -> "x86"
                }
            }
        }
    }
}
