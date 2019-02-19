package org.jetbrains.intellij.jbre

import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.VersionNumber
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.IntelliJPluginExtension

import java.nio.file.Paths

class JbreResolver {
    private final Project project
    private final String cacheDirectoryPath
    private OperatingSystem operatingSystem

    JbreResolver(@NotNull Project project) {
        this.project = project
        this.cacheDirectoryPath = Paths.get(project.gradle.gradleUserHomeDir.absolutePath, 'caches/modules-2/files-2.1/com.jetbrains/jbre').toString()
        this.operatingSystem = OperatingSystem.current()
    }

    @Nullable
    Jbre resolve(@Nullable String version) {
        if (version == null) {
            return null
        }
        def artifactName = getJbreArtifactName(version.startsWith('u') ? "8$version" : version)
        def javaDir = new File(cacheDirectoryPath, artifactName)
        if (javaDir.exists()) {
            if (javaDir.isDirectory()) {
                return new Jbre(version, javaDir, findJavaExecutable(javaDir))
            }
            javaDir.delete()
        }

        def javaArchive = getJavaArchive(artifactName)
        if (javaArchive != null) {
            untar(javaArchive, javaDir)
            javaArchive.delete()
            return new Jbre(version, javaDir, findJavaExecutable(javaDir))
        }
        return null
    }

    @NotNull
    private String getJbreArtifactName(@NotNull String version) {
        def lastIndexOfB = version.lastIndexOf('b')
        def majorVersion = lastIndexOfB > -1 ? version.substring(0, lastIndexOfB) : version
        def buildNumber = lastIndexOfB > -1 ? version.substring(lastIndexOfB + 1) : ''
        boolean oldFormat = version.startsWith('jbrex8') ||
                VersionNumber.parse(buildNumber) < VersionNumber.parse('1483.24')
        if (oldFormat) {
            majorVersion = !majorVersion.startsWith('jbrex8') ? "jbrex8${majorVersion}" : majorVersion
            return "${majorVersion}b${buildNumber}_${platform()}_${arch(false)}"
        }
        if (!majorVersion.startsWith('jbrsdk-') && !majorVersion.startsWith('jbrx-')) {
            majorVersion = majorVersion.startsWith('11') ? "jbrsdk-$majorVersion" : "jbrx-$majorVersion"
        }
        return "${majorVersion}-${platform()}-${arch(true)}-b${buildNumber}"
    }

    private File getJavaArchive(@NotNull String artifactName) {
        def archiveName = "${artifactName}.tar.gz"
        def javaArchive = new File(cacheDirectoryPath, archiveName)
        if (javaArchive.exists()) {
            return javaArchive
        }
        def intellijExtension = project.extensions.findByType(IntelliJPluginExtension)
        def repo = intellijExtension != null ? intellijExtension.jreRepo : null
        def url = "${repo ?: IntelliJPlugin.DEFAULT_JBRE_REPO}/$archiveName"
        try {
            new DownloadActionWrapper(project, url, javaArchive.absolutePath).execute()
            return javaArchive
        } catch (IOException e) {
            IntelliJPlugin.LOG.warn("Cannot download JetBrains Java Runtime $artifactName", e)
            return null
        }
    }

    private void untar(@NotNull File from, @NotNull File to) {
        def tempDir = new File(to.parent, to.name + "-temp")
        if (tempDir.exists()) {
            tempDir.deleteDir()
        }
        tempDir.mkdir()

        project.copy {
            it.from project.tarTree(from)
            it.into tempDir
        }
        tempDir.renameTo(to)
    }

    @Nullable
    private def findJavaExecutable(@NotNull File javaHome) {
        def java = new File(javaHome, operatingSystem.isMacOsX() ? 'jdk/Contents/Home/jre/bin/java' :
                operatingSystem.isWindows() ? 'jre/bin/java.exe' : 'jre/bin/java')
        return java.exists() ? java.absolutePath : null
    }

    private def platform() {
        def current = operatingSystem
        if (current.isWindows()) return 'windows'
        if (current.isMacOsX()) return 'osx'
        return 'linux'
    }

    private static def arch(boolean newFormat) {
        def arch = System.getProperty("os.arch")
        return 'x86' == arch ? (newFormat ? 'i586' : 'x86') : 'x64'
    }
}
