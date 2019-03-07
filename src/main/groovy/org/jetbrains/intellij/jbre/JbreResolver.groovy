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
    private final OperatingSystem operatingSystem

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
        def jbreArtifact = JbreArtifact.from(version.startsWith('u') ? "8$version" : version, operatingSystem)
        def javaDir = new File(cacheDirectoryPath, jbreArtifact.name)
        if (javaDir.exists()) {
            if (javaDir.isDirectory()) {
                return new Jbre(version, javaDir, findJavaExecutable(javaDir))
            }
            javaDir.delete()
        }

        def javaArchive = getJavaArchive(jbreArtifact)
        if (javaArchive != null) {
            untar(javaArchive, javaDir)
            javaArchive.delete()
            return new Jbre(version, javaDir, findJavaExecutable(javaDir))
        }
        return null
    }

    private File getJavaArchive(@NotNull JbreArtifact jbreArtifact) {
        def archiveName = "${jbreArtifact.name}.tar.gz"
        def javaArchive = new File(cacheDirectoryPath, archiveName)
        if (javaArchive.exists()) {
            return javaArchive
        }
        def intellijExtension = project.extensions.findByType(IntelliJPluginExtension)
        def repo = intellijExtension != null ? intellijExtension.jreRepo : null
        def url = "${repo ?: jbreArtifact.repoUrl}/$archiveName"
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

    private static class JbreArtifact {
        String name
        String repoUrl

        JbreArtifact(@NotNull String name, @NotNull String repoUrl) {
            this.name = name
            this.repoUrl = repoUrl
        }

        static JbreArtifact from(@NotNull String version, @NotNull OperatingSystem operatingSystem) {
            def prefix = getPrefix(version)
            def lastIndexOfB = version.lastIndexOf('b')
            def majorVersion = lastIndexOfB > -1
                    ? version.substring(prefix.length(), lastIndexOfB)
                    : version.substring(prefix.length())
            def buildNumberString = lastIndexOfB > -1 ? version.substring(lastIndexOfB + 1) : ''
            def buildNumber = VersionNumber.parse(buildNumberString)
            def isJava8 = majorVersion.startsWith('8')

            String repoUrl = !isJava8 || buildNumber >= VersionNumber.parse('1483.31')
                    ? IntelliJPlugin.DEFAULT_NEW_JBRE_REPO
                    : IntelliJPlugin.DEFAULT_JBRE_REPO

            boolean oldFormat = prefix == 'jbrex-' || isJava8 && buildNumber < VersionNumber.parse('1483.24')
            if (oldFormat) {
                return new JbreArtifact("jbrex-${majorVersion}b${buildNumberString}_${platform(operatingSystem)}_${arch(false)}", repoUrl)
            }

            if (!prefix) {
                prefix = isJava8 ? "jbrx-" : "jbr-"
            }
            return new JbreArtifact("$prefix${majorVersion}-${platform(operatingSystem)}-${arch(true)}-b${buildNumberString}", repoUrl)
        }

        private static String getPrefix(String version) {
            def prefix = version.substring(0, version.indexOf('-') + 1)
            if (prefix == 'jbrex-' || prefix == 'jbr-' || prefix == 'jbrx-') {
                return prefix
            }
            return ''
        }

        private static def platform(def operatingSystem) {
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
}
