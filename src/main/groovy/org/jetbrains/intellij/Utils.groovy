package org.jetbrains.intellij

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.base.utils.FileUtilKt
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.AbstractFileFilter
import org.apache.commons.io.filefilter.FalseFileFilter
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.JavaForkOptions
import org.gradle.util.CollectionUtils
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.dependency.IntellijIvyArtifact
import org.jetbrains.intellij.dependency.PluginDependencyNotation
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException

import java.util.function.BiConsumer
import java.util.function.Predicate
import java.util.regex.Pattern

class Utils {
    public static final Pattern VERSION_PATTERN = Pattern.compile('^([A-Z]+)-([0-9.A-z]+)\\s*$')

    @NotNull
    static SourceSet mainSourceSet(@NotNull Project project) {
        JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention)
        javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
    }

    @NotNull
    static IvyArtifact createJarDependency(File file, String configuration, File baseDir, String classifier = null) {
        return createDependency(baseDir, file, configuration, "jar", "jar", classifier)
    }

    @NotNull
    static IvyArtifact createDirectoryDependency(File file, String configuration, File baseDir, String classifier = null) {
        return createDependency(baseDir, file, configuration, "", "directory", classifier)
    }

    private static IvyArtifact createDependency(File baseDir, File file, String configuration, String extension, String type, String classifier) {
        def relativePath = baseDir.toURI().relativize(file.toURI()).getPath()
        def name = extension ? relativePath - ".$extension" : relativePath
        def artifact = new IntellijIvyArtifact(file, name, extension, type, classifier)
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
                } catch (SAXParseException e) {
                    warn(project, "Cannot read ${pluginXml}. Skipping", e)
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

    @NotNull
    static List<String> getIdeJvmArgs(@NotNull JavaForkOptions options,
                                      @NotNull List<String> originalArguments,
                                      @NotNull File ideDirectory) {
        if (options.maxHeapSize == null) options.maxHeapSize = "512m"
        if (options.minHeapSize == null) options.minHeapSize = "256m"
        List<String> result = new ArrayList<String>(originalArguments)
        def bootJar = new File(ideDirectory, "lib/boot.jar")
        if (bootJar.exists()) result.add("-Xbootclasspath/a:$bootJar.absolutePath")
        return result
    }

    @NotNull
    static File ideSdkDirectory(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        def path = extension.alternativeIdePath
        if (path) {
            def dir = ideaDir(path)
            if (!dir.exists()) {
                def ideDirectory = extension.ideaDependency.classes
                error(project, "Cannot find alternate SDK path: $dir. Default IDE will be used : $ideDirectory")
                return ideDirectory
            }
            return dir
        }
        return extension.ideaDependency.classes
    }

    @NotNull
    static String ideBuildNumber(@NotNull File ideDirectory) {
        if (OperatingSystem.current().isMacOsX()) {
            def file = new File(ideDirectory, "Resources/build.txt")
            if (file.exists()) {
                return file.getText('UTF-8').trim()
            }
        }
        return new File(ideDirectory, "build.txt").getText('UTF-8').trim()
    }

    @NotNull
    static File ideaDir(@NotNull String path) {
        File dir = new File(path)
        return dir.name.endsWith(".app") ? new File(dir, "Contents") : dir
    }

    // todo: collect all ids for multi-project configuration
    static List<String> getPluginIds(@NotNull Project project) {
        Set<String> ids = new HashSet<>()
        sourcePluginXmlFiles(project).files.each {
            def pluginXml = parseXml(it)
            ids += pluginXml.id*.text()
        }
        return ids.size() == 1 ? [ids.first()] : Collections.<String> emptyList()
    }

    static Node parseXml(File file) {
        return parseXml(new FileInputStream(file))
    }

    static Node parseXml(InputStream inputStream) {
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
        return FileUtilKt.isJar(file.toPath())
    }

    static boolean isZipFile(@NotNull File file) {
        return FileUtilKt.isZip(file.toPath())
    }

    @NotNull
    static PluginDependencyNotation parsePluginDependencyString(@NotNull String s) {
        if (new File(s).exists()) {
            return new PluginDependencyNotation(s, null, null)
        }

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
        return new PluginDependencyNotation(id ?: null, version ?: null, channel ?: null)
    }

    static String stringInput(input) {
        input = input instanceof Closure ? (input as Closure).call() : input
        return input?.toString()
    }

    static List<String> stringListInput(input) {
        return CollectionUtils.stringize(input.collect {
            it instanceof Closure ? (it as Closure).call() : it
        }.flatten())
    }

    @NotNull
    static Collection<File> collectJars(@NotNull File directory, @NotNull final Predicate<File> filter) {
        if (!directory.isDirectory()) {
            return Collections.emptyList();
        }

        return FileUtils.listFiles(directory, new AbstractFileFilter() {
            @Override
            boolean accept(File file) {
                return isJarFile(file) && filter.test(file)
            }
        }, FalseFileFilter.FALSE)
    }

    static String resolveToolsJar(String javaExec) {
        String binDir = new File(javaExec).parent
        if (OperatingSystem.current().isMacOsX()) {
            return "$binDir/../../lib/tools.jar"
        }
        return "$binDir/../lib/tools.jar"
    }

    static String getBuiltinJbrVersion(@NotNull File ideDirectory) {
        def dependenciesFile = new File(ideDirectory, "dependencies.txt")
        if (dependenciesFile.exists()) {
            def properties = new Properties()
            def reader = new FileReader(dependenciesFile)
            try {
                properties.load(reader)
                return properties.getProperty('jdkBuild')
            }
            catch (IOException ignore) {
            }
            finally {
                reader.close()
            }
        }
        return null
    }

    static def unzip(@NotNull File zipFile,
                     @NotNull File cacheDirectory,
                     @NotNull Project project,
                     @Nullable Predicate<File> isUpToDate,
                     @Nullable BiConsumer<File, File> markUpToDate,
                     @Nullable String targetDirName = null) {
        def targetDirectory = new File(cacheDirectory, targetDirName ?: zipFile.name - ".zip")
        def markerFile = new File(targetDirectory, "markerFile")
        if (markerFile.exists() && (isUpToDate == null || isUpToDate.test(markerFile))) {
            return targetDirectory
        }

        if (targetDirectory.exists()) {
            targetDirectory.deleteDir()
        }
        targetDirectory.mkdir()

        debug(project, "Unzipping ${zipFile.name}")
        project.copy {
            it.from(project.zipTree(zipFile))
            it.into(targetDirectory)
        }
        debug(project, "Unzipped ${zipFile.name}")

        markerFile.createNewFile()
        if (markUpToDate != null) {
            markUpToDate.accept(targetDirectory, markerFile)
        }
        return targetDirectory
    }

    private static Pattern MAJOR_VERSION_PATTERN = Pattern.compile('(RIDER-)?\\d{4}\\.\\d-SNAPSHOT')

    static String releaseType(@NotNull String version) {
        if (version.endsWith('-EAP-SNAPSHOT') || version.endsWith('-EAP-CANDIDATE-SNAPSHOT') || version.endsWith('-CUSTOM-SNAPSHOT') || MAJOR_VERSION_PATTERN.matcher(version).matches()) {
            return 'snapshots'
        }
        if (version.endsWith('-SNAPSHOT')) {
            return 'nightly'
        }
        return 'releases'
    }

    static def error(def context, String message, Throwable e = null) {
        log(LogLevel.ERROR, context, message, e)
    }

    static def warn(def context, String message, Throwable e = null) {
        log(LogLevel.WARN, context, message, e)
    }

    static def info(def context, String message, Throwable e = null) {
        log(LogLevel.INFO, context, message, e)
    }

    static def debug(def context, String message, Throwable e = null) {
        log(LogLevel.DEBUG, context, message, e)
    }

    private static log(LogLevel level, def context, String message, Throwable e) {
        if (e != null) {
            if (level != LogLevel.ERROR && !IntelliJPlugin.LOG.isDebugEnabled()) {
                e = null
                message += ". Run with --debug option to get more log output."
            }
        }
        def category = "gradle-intellij-plugin"
        if (context instanceof Project) {
            category += " :${(context as Project).name}"
        }
        if (context instanceof Task) {
            category += " :${(context as Task).project.name}:${(context as Task).name}"
        }
        IntelliJPlugin.LOG.log(level, "[$category] $message", e as Throwable)
    }

    static IdePlugin createPlugin(@NotNull File artifact, boolean validatePluginXml, def loggingContext) {
        def creationResult = IdePluginManager.createManager().createPlugin(artifact.toPath(), validatePluginXml, IdePluginManager.PLUGIN_XML)
        if (creationResult instanceof PluginCreationSuccess) {
            return creationResult.plugin as IdePlugin
        } else if (creationResult instanceof PluginCreationFail) {
            def problems = creationResult.errorsAndWarnings.findAll { it.level == PluginProblem.Level.ERROR }.join(", ")
            warn(loggingContext, "Cannot create plugin from file ($artifact): $problems")
        } else {
            warn(loggingContext, "Cannot create plugin from file ($artifact). $creationResult")
        }
        return null
    }

    static void untar(@NotNull Project project, @NotNull File from, @NotNull File to) {
        def tempDir = new File(to.parent, to.name + "-temp")
        debug(project, "Unpacking $from.absolutePath to $tempDir.absolutePath")

        if (tempDir.exists()) {
            tempDir.deleteDir()
        }
        tempDir.mkdir()

        if (OperatingSystem.current().isWindows()) {
            project.copy {
                it.from project.tarTree(from)
                it.into tempDir
            }
        } else {
            project.exec {
                commandLine 'tar', '-xpf', from.absolutePath, '--directory', tempDir.absolutePath
            }
        }
        tempDir.renameTo(to)
    }
}
