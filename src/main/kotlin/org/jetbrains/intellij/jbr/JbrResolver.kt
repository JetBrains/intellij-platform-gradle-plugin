package org.jetbrains.intellij.jbr

import de.undercouch.gradle.tasks.download.DownloadAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.VersionNumber
import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.extractArchive
import org.jetbrains.intellij.warn
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions

open class JbrResolver(
    private val downloadAction: DownloadAction,
    private val jreRepository: String,
    gradleUserHomeDir: String,
    private val isOffline: Boolean,
    private val context: Any,
) {

    private val cacheDirectoryPath = Paths.get(gradleUserHomeDir, "caches/modules-2/files-2.1/com.jetbrains/jbre").toString()
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
            println("javaDir=$javaDir")
            extractArchive(it, javaDir, context)
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
        try {
            Files.setPosixFilePermissions(javaExecutable, PosixFilePermissions.fromString("rwxr-xr-x"))
        } catch (e: Exception) {
            println("Files.setPosixFilePermissions=$e")
            throw e
        }
        return Jbr(version, javaDir, javaExecutable.toFile().absolutePath)
    }

    private fun getJavaArchive(jbrArtifact: JbrArtifact): File? {
        val artifactName = jbrArtifact.name
        val archiveName = "${artifactName}.tar.gz"
        val javaArchive = File(cacheDirectoryPath, archiveName)
        if (javaArchive.exists()) {
            return javaArchive
        }

        if (isOffline) {
            warn(context, "Cannot download JetBrains Java Runtime $artifactName. Gradle runs in offline mode.")
            return null
        }

        val url = "${jreRepository.takeIf { it.isNotEmpty() } ?: jbrArtifact.repositoryUrl}/$archiveName"
        return try {
            downloadAction.apply {
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

    private fun findJavaExecutable(javaHome: File): Path? {
        val root = getJbrRoot(javaHome)
        val jre = File(root, "jre")
        val java = File(
            jre.takeIf { it.exists() } ?: root,
            "bin/java" + (".exe".takeIf { operatingSystem.isWindows } ?: "")
        )
        return java.toPath().takeIf { java.exists() }
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
