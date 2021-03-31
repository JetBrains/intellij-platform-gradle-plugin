package org.jetbrains.intellij.jbr

import de.undercouch.gradle.tasks.download.DownloadAction
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.VersionNumber
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.Utils

import java.nio.file.Paths

class JbrResolver {
    private final Project project
    private final String cacheDirectoryPath
    private final OperatingSystem operatingSystem
    private final def context

    JbrResolver(@NotNull Project project, @Nullable Task context) {
        this.context = context ?: project
        this.project = project
        this.cacheDirectoryPath = Paths.get(project.gradle.gradleUserHomeDir.absolutePath, 'caches/modules-2/files-2.1/com.jetbrains/jbre').toString()
        this.operatingSystem = OperatingSystem.current()
    }

    @Nullable
    Jbr resolve(@Nullable String version) {
        if (version == null || version.isEmpty()) {
            return null
        }
        def jbrArtifact = JbrArtifact.from(version.startsWith('u') ? "8$version" : version, operatingSystem)
        def javaDir = new File(cacheDirectoryPath, jbrArtifact.name)
        if (javaDir.exists()) {
            if (javaDir.isDirectory()) {
                return fromDir(javaDir, version)
            }
            javaDir.delete()
        }

        def javaArchive = getJavaArchive(jbrArtifact)
        if (javaArchive != null) {
            Utils.untar(project, javaArchive, javaDir)
            javaArchive.delete()
            return fromDir(javaDir, version)
        }
        return null
    }

    @Nullable
    private Jbr fromDir(@NotNull File javaDir, @NotNull String version) {
        def javaExecutable = findJavaExecutable(javaDir)
        if (javaExecutable == null) {
            Utils.warn(context, "Cannot find java executable in $javaDir")
            return null
        }
        return new Jbr(version, javaDir, findJavaExecutable(javaDir))
    }

    private File getJavaArchive(@NotNull JbrArtifact jbrArtifact) {
        def artifactName = jbrArtifact.name
        def archiveName = "${artifactName}.tar.gz"
        def javaArchive = new File(cacheDirectoryPath, archiveName)
        if (javaArchive.exists()) {
            return javaArchive
        }

        if (project.gradle.startParameter.offline) {
            Utils.warn(context, "Cannot download JetBrains Java Runtime $artifactName. Gradle runs in offline mode.")
            return null
        }

        def intellijExtension = project.extensions.findByType(IntelliJPluginExtension)
        def repo = intellijExtension != null ? intellijExtension.jreRepo : null
        def url = "${repo ?: jbrArtifact.repoUrl}/$archiveName"
        try {
            new DownloadAction(project).with {
                src(url)
                dest(javaArchive.absolutePath)
                tempAndMove(true)
                execute()
            }
            return javaArchive
        } catch (IOException e) {
            Utils.warn(context, "Cannot download JetBrains Java Runtime $artifactName", e)
            return null
        }
    }

    @Nullable
    private def findJavaExecutable(@NotNull File javaHome) {
        def root = getJbrRoot(javaHome)
        def jre = new File(root, 'jre')
        def java = new File(jre.exists() ? jre : root, operatingSystem.isWindows() ? 'bin/java.exe' : 'bin/java')
        return java.exists() ? java.absolutePath : null
    }

    private def getJbrRoot(@NotNull File javaHome) {
        File jbr
        for (folder in javaHome.listFiles()) {
            if (folder.name in ["jbr", "jbrsdk"] ) {
                jbr = folder
                break
            }
        }
        if (jbr != null && jbr.exists()) {
            return operatingSystem.isMacOsX() ? new File(jbr, 'Contents/Home') : jbr
        }
        return new File(javaHome, operatingSystem.isMacOsX() ? 'jdk/Contents/Home' : '')
    }

    private static class JbrArtifact {
        String name
        String repoUrl

        JbrArtifact(@NotNull String name, @NotNull String repoUrl) {
            this.name = name
            this.repoUrl = repoUrl
        }

        static JbrArtifact from(@NotNull String version, @NotNull OperatingSystem operatingSystem) {
            def prefix = getPrefix(version)
            def lastIndexOfB = version.lastIndexOf('b')
            def majorVersion = lastIndexOfB > -1
                    ? version.substring(prefix.length(), lastIndexOfB)
                    : version.substring(prefix.length())
            def buildNumberString = lastIndexOfB > -1 ? version.substring(lastIndexOfB + 1) : ''
            def buildNumber = VersionNumber.parse(buildNumberString)
            def isJava8 = majorVersion.startsWith('8')

            String repoUrl = IntelliJPlugin.DEFAULT_JBR_REPO
            boolean oldFormat = prefix == 'jbrex' || isJava8 && buildNumber < VersionNumber.parse('1483.24')
            if (oldFormat) {
                return new JbrArtifact("jbrex${majorVersion}b${buildNumberString}_${platform(operatingSystem)}_${arch(false)}", repoUrl)
            }

            if (!prefix) {
                if (isJava8) {
                    prefix = "jbrx-"
                } else if (buildNumber < VersionNumber.parse('1319.6')) {
                    prefix = "jbr-"
                } else {
                    prefix = "jbr_jcef-"
                }
            }
            return new JbrArtifact("$prefix${majorVersion}-${platform(operatingSystem)}-${arch(isJava8)}-b${buildNumberString}", repoUrl)
        }

        private static String getPrefix(String version) {
            if (version.startsWith('jbrsdk-')) {
                return 'jbrsdk-'
            }
            if (version.startsWith('jbr_jcef-')) {
                return 'jbr_jcef-'
            }
            if (version.startsWith('jbr-')) {
                return 'jbr-'
            }
            if (version.startsWith('jbrx-')) {
                return 'jbrx-'
            }
            if (version.startsWith('jbrex8')) {
                return 'jbrex'
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
            if ('aarch64' == arch || 'arm64' == arch) {
                return 'aarch64'
            }
            if ('x86_64' == arch || 'amd64' == arch) {
                return 'x64'
            }
            def name = System.getProperty("os.name")
            if (name.contains("Windows") && System.getenv("ProgramFiles(x86)") != null) {
                return 'x64'
            }
            return newFormat ? 'i586' : 'x86'
        }
    }
}
