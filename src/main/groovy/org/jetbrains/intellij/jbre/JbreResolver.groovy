package org.jetbrains.intellij.jbre

import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
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
        def artifactName
        if (!version.startsWith('jbrex8') && !version.startsWith('u')) {
            version = !version.startsWith("jbrsdk-") ? "jbrsdk-${version}" : version
            def lastIndexOfB = version.lastIndexOf('b')
            def majorVersion = lastIndexOfB > -1 ? version.substring(0, lastIndexOfB) : version
            def buildNumber = lastIndexOfB > -1 ? version.substring(lastIndexOfB) : ''
            artifactName = "${majorVersion}-${platform()}-${arch(true)}-${buildNumber}"
        } else {
            // old jbre
            version = !version.startsWith("jbrex8") ? "jbrex8${version}" : version
            artifactName = "${version}_${platform()}_${arch(false)}"
        }
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
