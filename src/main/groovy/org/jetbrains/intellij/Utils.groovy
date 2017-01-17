package org.jetbrains.intellij

import com.intellij.structure.impl.utils.StringUtil
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifact
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.JavaForkOptions
import org.jetbrains.annotations.NotNull
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException

import java.util.regex.Pattern

class Utils {
    public static final Pattern VERSION_PATTERN = Pattern.compile('^([A-Z]{2})-([0-9.A-z]+)\\s*$')

    @NotNull
    static SourceSet mainSourceSet(@NotNull Project project) {
        JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention)
        javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
    }

    @NotNull
    static SourceSet testSourceSet(@NotNull Project project) {
        JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention)
        javaConvention.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)
    }

    @NotNull
    static DefaultIvyArtifact createJarDependency(File file, String configuration, File baseDir) {
        return createDependency(baseDir, file, configuration, "jar", "jar")
    }

    @NotNull
    static DefaultIvyArtifact createDirectoryDependency(File file, String configuration, File baseDir) {
        return createDependency(baseDir, file, configuration, "", "directory")
    }

    private static DefaultIvyArtifact createDependency(File baseDir, File file, String configuration,
                                                       String extension, String type) {
        def relativePath = baseDir.toURI().relativize(file.toURI()).getPath()
        def name = extension ? relativePath - ".$extension" : relativePath
        def artifact = new DefaultIvyArtifact(file, name, extension, type, null)
        artifact.conf = configuration
        return artifact
    }

    @NotNull
    static FileCollection sourcePluginXmlFiles(@NotNull Project project) {
        Set<File> result = new HashSet<>()
        mainSourceSet(project).resources.srcDirs.each {
            def pluginXml = new File(it, "META-INF/plugin.xml")
            if (pluginXml.exists()) {
                try {
                    if (parseXml(pluginXml).name() == 'idea-plugin') {
                        result += pluginXml
                    }
                } catch (SAXParseException ignore) {
                    IntelliJPlugin.LOG.warn("Cannot read ${pluginXml}. Skipping.")
                    IntelliJPlugin.LOG.debug("Cannot read ${pluginXml}", ignore)
                }
            }
        }
        project.files(result)
    }

    @NotNull
    static Map<String, Object> getIdeaSystemProperties(@NotNull File configDirectory,
                                                       @NotNull File systemDirectory,
                                                       @NotNull File pluginsDirectory,
                                                       @NotNull List<String> requirePluginIds) {
        def result = ["idea.config.path" : configDirectory.absolutePath,
                      "idea.system.path" : systemDirectory.absolutePath,
                      "idea.plugins.path": pluginsDirectory.absolutePath]
        if (!requirePluginIds.empty) {
            result.put("idea.required.plugins.id", requirePluginIds.join(","))
        }
        result
    }

    static def configDir(@NotNull String sandboxDirectoryPath, boolean inTests) {
        def suffix = inTests ? "-test" : ""
        "$sandboxDirectoryPath/config$suffix"
    }

    static def systemDir(@NotNull String sandboxDirectoryPath, boolean inTests) {
        def suffix = inTests ? "-test" : ""
        "$sandboxDirectoryPath/system$suffix"
    }

    static def pluginsDir(@NotNull String sandboxDirectoryPath, boolean inTests) {
        def suffix = inTests ? "-test" : ""
        "$sandboxDirectoryPath/plugins$suffix"
    }

    @NotNull
    static List<String> getIdeaJvmArgs(@NotNull JavaForkOptions options,
                                       @NotNull List<String> originalArguments,
                                       @NotNull File ideaDirectory) {
        if (options.maxHeapSize == null) options.maxHeapSize = "512m"
        if (options.minHeapSize == null) options.minHeapSize = "256m"
        boolean hasPermSizeArg = false
        def result = []
        for (String arg : originalArguments) {
            if (arg.startsWith("-XX:MaxPermSize")) {
                hasPermSizeArg = true
            }
            result += arg
        }

        result += "-Xbootclasspath/a:${ideaDirectory.absolutePath}/lib/boot.jar"
        if (!hasPermSizeArg) result += "-XX:MaxPermSize=250m"
        return result
    }

    static File projectDirectory(@NotNull IntelliJPluginExtension extension) {
        def path = extension.projectDirectory
        IntelliJPlugin.LOG.warn("Trying to assign project path: $path")
        def dir = new File(path)

        if (path) {
            if (!dir.exists()) {
                IntelliJPlugin.LOG.error("Cannot find IntelliJ project: $dir.")
            } else {
                return dir
            }
        }

        return null
    }

    @NotNull
    static File ideaSdkDirectory(@NotNull IntelliJPluginExtension extension) {
        def path = extension.alternativeIdePath
        IntelliJPlugin.LOG.warn("Trying to assign alternative IDE path: $path")
        if (path) {
            def dir = new File(path)
            if (dir.getName().endsWith(".app")) {
                dir = new File(dir, "Contents")
            }
            if (!dir.exists()) {
                def ideaDirectory = extension.ideaDependency.classes
                IntelliJPlugin.LOG.error("Cannot find alternate SDK path: $dir. Default IDEA will be used : $ideaDirectory")
                return ideaDirectory
            }
            return dir
        }
        return extension.ideaDependency.classes
    }

    @NotNull
    static String ideaBuildNumber(@NotNull File ideaDirectory) {
        if (OperatingSystem.current().isMacOsX()) {
            def file = new File(ideaDirectory, "Resources/build.txt")
            if (file.exists()) {
                return file.getText('UTF-8').trim()
            }
        }
        return new File(ideaDirectory, "build.txt").getText('UTF-8').trim()
    }

    // todo: collect all ids for multiproject configuration
    static def getPluginIds(@NotNull Project project) {
        Set<String> ids = new HashSet<>()
        sourcePluginXmlFiles(project).files.each {
            def pluginXml = parseXml(it)
            ids += pluginXml.id*.text()
        }
        return ids.size() == 1 ? [ids.first()] : Collections.emptyList()
    }

    static Node parseXml(File file) {
        def parser = new XmlParser(false, true, true)
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        parser.setErrorHandler(new ErrorHandler() {
            @Override
            void warning(SAXParseException e) throws SAXException {

            }

            @Override
            void error(SAXParseException e) throws SAXException {
                throw e
            }

            @Override
            void fatalError(SAXParseException e) throws SAXException {
                throw e
            }
        })
        InputStream inputStream = new FileInputStream(file)
        InputSource input = new InputSource(new InputStreamReader(inputStream, "UTF-8"))
        input.setEncoding("UTF-8")
        try {
            return parser.parse(input)
        }
        finally {
            inputStream.close()
        }
    }

    static boolean isJarFile(@NotNull File file) {
        return StringUtil.endsWithIgnoreCase(file.name, ".jar")
    }

    static boolean isZipFile(@NotNull File file) {
        return StringUtil.endsWithIgnoreCase(file.name, ".zip")
    }

    @NotNull
    static parsePluginDependencyString(@NotNull String s) {
        def id = null, version = null, channel = null
        def idAndVersion = s.split('[:]', 2)
        if (idAndVersion.length == 1) {
            def idAndChannel = idAndVersion[0].split('[@]', 2)
            id = idAndChannel[0]
            channel = idAndChannel.length > 1 ? idAndChannel[1] : null
        } else if (idAndVersion.length == 2) {
            def versionAndChannel = idAndVersion[1].split('[@]', 2)
            id = idAndVersion[0]
            version = versionAndChannel[0]
            channel = versionAndChannel.length > 1 ? versionAndChannel[1] : null
        }
        return new Tuple(id ?: null, version ?: null, channel ?: null)
    }

    static String stringInput(input) {
        input = input instanceof Closure ? (input as Closure).call() : input
        return input?.toString()
    }
}
