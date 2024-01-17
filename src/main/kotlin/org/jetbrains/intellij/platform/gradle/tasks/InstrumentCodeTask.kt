// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("SameReturnValue")

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.base.utils.deleteLogged
import com.jetbrains.plugin.structure.base.utils.deleteQuietly
import groovy.lang.Closure
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.dependency.IdeaDependency
import org.jetbrains.intellij.platform.gradle.utils.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

@Deprecated("Use 'InstrumentCodeTask' instead", ReplaceWith("InstrumentCodeTask"), DeprecationLevel.ERROR)
typealias IntelliJInstrumentCodeTask = InstrumentCodeTask

/**
 * The following attributes help you to tune instrumenting behaviour in `instrumentCode { ... }` block.
 */
@Deprecated(message = "CHECK")
@CacheableTask
abstract class InstrumentCodeTask : DefaultTask() {

    /**
     * Compile classpath of the project's source set.
     */
    @get:Internal
    abstract val sourceSetCompileClasspath: ConfigurableFileCollection

    /**
     * The dependency on IntelliJ IDEA.
     *
     * Default value: [SetupDependenciesTask.idea]
     */
    @get:Input
    @get:Optional
    abstract val ideaDependency: Property<IdeaDependency>

    /**
     * Path to the `javac2.jar` file of the IntelliJ IDEA.
     *
     * Default value: `lib/javac2.jar` resolved in [ideaDependency]
     */
    @get:Input
    @get:Optional
    abstract val javac2: Property<File>

    /**
     * A version of instrumenting compiler.
     * It's used in cases when targeting non-IntelliJ IDEA IDEs (e.g. CLion or Rider).
     *
     * Default value: Build number of the IDE dependency.
     */
    @get:Input
    abstract val compilerVersion: Property<String>

    /**
     * The list of directories with compiled classes.
     *
     * Default value: `classesDirs` of the project's source sets.
     */
    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classesDirs: ConfigurableFileCollection

    /**
     * The list of directories with GUI Designer form files.
     *
     * Default value: `.form` files of the project's source sets.
     */
    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val formsDirs: ConfigurableFileCollection

    @get:Internal
    abstract val sourceDirs: ConfigurableFileCollection

    @get:Internal
    abstract val instrumentationLogs: Property<Boolean>

    /**
     * The output directory for instrumented classes.
     *
     * Default value: [SetupInstrumentCodeTask.instrumentedDir]
     */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /**
     * The classpath for Java instrumentation compiler.
     *
     * // TODO ConfigurableFilesCollection
     */
    @get:Input
    abstract val compilerClassPathFromMaven: ListProperty<File>

    private val context = logCategory()

    init {
        group = PLUGIN_GROUP_NAME
        description = "Code instrumentation task."
    }

    @TaskAction
    fun intelliJInstrumentCode(inputChanges: InputChanges) {
        val classpath = compilerClassPath()

        ant.invokeMethod(
            "taskdef",
            mapOf(
                "name" to "instrumentIdeaExtensions",
                "classpath" to classpath.joinToString(":"),
                "loaderref" to LOADER_REF,
                "classname" to "com.intellij.ant.InstrumentIdeaExtensions",
            ),
        )

        info(context, "Compiling forms and instrumenting code with nullability preconditions")
        val instrumentNotNull = prepareNotNullInstrumenting(classpath)

        val outputDirPath = outputDir.asPath
        val temporaryDirPath = temporaryDir
            .toPath()
            .also {
                it.deleteQuietly()
                it.createDirectories()
            }

        inputChanges.getFileChanges(formsDirs).forEach { change ->
            val path = change.file.toPath()
            val sourceDir = sourceDirs
                .find { path.startsWith(it.toPath()) }
                ?.toPath()
                ?: return@forEach
            val relativePath = sourceDir.relativize(path)

            val compiledClassRelativePath = relativePath.toString().replace(".form", ".class")
            val compiledClassPath = classesDirs.asFileTree
                .find { it.endsWith(compiledClassRelativePath) }
                ?.takeIf { it.exists() }
                ?.toPath()
                ?: return@forEach
            val instrumentedClassPath = temporaryDirPath
                .resolve(compiledClassRelativePath)
                .also {
                    if (!it.exists()) {
                        it.createDirectories()
                    }
                }

            Files.copy(compiledClassPath, instrumentedClassPath, StandardCopyOption.REPLACE_EXISTING)
        }

        inputChanges.getFileChanges(classesDirs).forEach { change ->
            if (change.fileType != FileType.FILE) {
                return@forEach
            }
            val path = change.file.toPath()
            val sourceDir = classesDirs.find { classesDir ->
                path.startsWith(classesDir.toPath())
            }?.toPath() ?: return@forEach
            val relativePath = sourceDir.relativize(path)

            when (change.changeType) {
                ChangeType.REMOVED -> listOf(outputDirPath, temporaryDirPath).forEach {
                    it.resolve(relativePath).deleteLogged()
                }

                else -> temporaryDirPath.resolve(relativePath).apply {
                    parent.createDirectories()
                    Files.copy(path, this, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        instrumentCode(instrumentNotNull) {
            Files.walk(temporaryDirPath)
                .filter { !it.isDirectory() }
                .forEach { path ->
                    val relativePath = temporaryDirPath.relativize(path)
                    outputDirPath.resolve(relativePath).apply {
                        parent.createDirectories()
                        Files.copy(path, this, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
        }
    }

    // local compiler
    private fun compilerClassPath() = javac2.orNull
        ?.let(File::toPath)
        ?.takeIf(Path::exists)
        ?.let { path ->
            ideaDependency.get().classes.toPath().resolve("lib").listDirectoryEntries().filter {
                listOf(
                    "jdom.jar",
                    "asm-all.jar",
                    "asm-all-*.jar",
                    "jgoodies-forms.jar",
                    "forms-*.jar",
                ).any { pattern ->
                    val (first, last) = pattern.split('*') + listOf("")
                    it.name.startsWith(first) && (last.isEmpty() || it.name.endsWith(last))
                }
            } + listOf(path)
        }
        .or { compilerClassPathFromMaven.get().map(File::toPath) }

    private fun prepareNotNullInstrumenting(classpath: List<Path>): Boolean {
        try {
            ant.invokeMethod(
                "typedef",
                mapOf(
                    "name" to "skip",
                    "classpath" to classpath.map(Path::toAbsolutePath).joinToString(":"),
                    "loaderref" to LOADER_REF,
                    "classname" to FILTER_ANNOTATION_REGEXP_CLASS,
                ),
            )
        } catch (e: BuildException) {
            val cause = e.cause
            if (cause is ClassNotFoundException && FILTER_ANNOTATION_REGEXP_CLASS == cause.message) {
                info(context, "Old version of Javac2 is used, instrumenting code with nullability will be skipped. Use IDEA >14 SDK (139.*) to fix this")
                return false
            } else {
                throw e
            }
        }
        return true
    }

    private fun instrumentCode(instrumentNotNull: Boolean, block: () -> Unit) {
        val headlessOldValue = System.setProperty("java.awt.headless", "true")
        try {
            // Builds up the Ant XML:
            // <instrumentIdeaExtensions srcdir="..." ...>
            //    <skip pattern=".."/>
            // </instrumentIdeaExtensions>

            val dirs = sourceDirs.filter { it.exists() }
            if (!dirs.isEmpty) {
                if (instrumentationLogs.get()) {
                    ant.lifecycleLogLevel = org.gradle.api.AntBuilder.AntMessagePriority.INFO
                }
                ant.invokeMethod("instrumentIdeaExtensions", arrayOf(
                    mapOf(
                        "srcdir" to dirs.joinToString(":"),
                        "destdir" to temporaryDir,
                        "classpath" to (sourceSetCompileClasspath + classesDirs).joinToString(":"),
                        "includeantruntime" to false,
                        "instrumentNotNull" to instrumentNotNull
                    ),
                    object : Closure<Any>(this, this) {
                        @Suppress("unused") // Groovy calls using reflection inside Closure
                        fun doCall() = when {
                            instrumentNotNull -> {
                                ant.invokeMethod(
                                    "skip", mapOf(
                                        "pattern" to "kotlin/Metadata"
                                    )
                                )
                            }

                            else -> null
                        }
                    }
                ))
            }
        } finally {
            block()

            if (headlessOldValue != null) {
                System.setProperty("java.awt.headless", headlessOldValue)
            } else {
                System.clearProperty("java.awt.headless")
            }
        }
    }

    companion object {
        const val FILTER_ANNOTATION_REGEXP_CLASS = "com.intellij.ant.ClassFilterAnnotationRegexp"
        const val LOADER_REF = "java2.loader"
    }

//    {
//        info(context, "Configuring compile tasks")
//
//        val instrumentedJar = project.configurations.create(IntelliJPluginConstants.INSTRUMENTED_JAR_CONFIGURATION_NAME)
//            .apply {
//                isCanBeConsumed = true
//                isCanBeResolved = false
//
//                extendsFrom(project.configurations["implementation"], project.configurations["runtimeOnly"])
//            }
//
//        val jarTaskProvider = project.tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME)
//        val instrumentCodeProvider = project.provider { extension.instrumentCode.get() }
//
//        val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer
//        sourceSets.forEach { sourceSet ->
//            val name = sourceSet.getTaskName("instrument", "code")
//            val instrumentTaskProvider = project.tasks.register<InstrumentCodeTask>(name) {
//                val taskContext = logCategory()
//
//                sourceDirs.from(project.provider {
//                    sourceSet.allJava.srcDirs
//                })
//                formsDirs.from(project.provider {
//                    sourceDirs.asFileTree.filter {
//                        it.toPath().extension == "form"
//                    }
//                })
//                classesDirs.from(project.provider {
//                    (sourceSet.output.classesDirs as ConfigurableFileCollection).from.run {
//                        project.files(this).filter { it.exists() }
//                    }
//                })
//                sourceSetCompileClasspath.from(project.provider {
//                    sourceSet.compileClasspath
//                })
//                compilerVersion.convention(ideaDependencyProvider.map {
//                    val productInfo = it.classes.toPath().productInfo()
//
//                    val version = extension.getVersionNumber().orNull.orEmpty()
//                    val type = extension.getVersionType().orNull.orEmpty().let {
//                        IntelliJPlatformType.fromCode(it)
//                    }
//                    val localPath = extension.localPath.orNull.orEmpty()
//                    val types = listOf(
//                        IntelliJPlatformType.CLion,
//                        IntelliJPlatformType.Rider,
//                        IntelliJPlatformType.PyCharmProfessional,
//                        IntelliJPlatformType.PhpStorm,
//                        IntelliJPlatformType.RustRover
//                    )
//
//                    when {
//                        localPath.isNotBlank() || !version.endsWith(IntelliJPluginConstants.RELEASE_SUFFIX_SNAPSHOT) -> {
//                            val eapSuffix = IntelliJPluginConstants.RELEASE_SUFFIX_EAP.takeIf { productInfo.versionSuffix == "EAP" }.orEmpty()
//                            IdeVersion.createIdeVersion(it.buildNumber).stripExcessComponents().asStringWithoutProductCode() + eapSuffix
//                        }
//
//                        version == IntelliJPluginConstants.DEFAULT_IDEA_VERSION && types.contains(type) -> {
//                            val buildNumber = productInfo.buildNumber.toVersion()
//                            "${buildNumber.major}.${buildNumber.minor}${IntelliJPluginConstants.RELEASE_SUFFIX_EAP_CANDIDATE}"
//                        }
//
//                        else -> {
//                            val prefix = when (type) {
//                                IntelliJPlatformType.CLion -> "CLION-"
//                                IntelliJPlatformType.Rider -> "RIDER-"
//                                IntelliJPlatformType.PyCharmProfessional -> "PYCHARM-"
//                                IntelliJPlatformType.PhpStorm -> "PHPSTORM-"
//                                IntelliJPlatformType.RustRover -> "RUSTROVER-"
//                                else -> ""
//                            }
//                            prefix + version
//                        }
//                    }
//                })
//                ideaDependency.convention(ideaDependencyProvider)
//                javac2.convention(ideaDependencyProvider.map {
//                    it.classes.resolve("lib/javac2.jar")
//                })
//                compilerClassPathFromMaven.convention(compilerVersion.map { compilerVersion ->
//                    if (compilerVersion == IntelliJPluginConstants.DEFAULT_IDEA_VERSION || compilerVersion.toVersion() >= Version(183, 3795, 13)) {
//                        val downloadCompiler = { version: String ->
//                            dependenciesDownloader.downloadFromMultipleRepositories(taskContext, {
//                                create(
//                                    group = "com.jetbrains.intellij.java",
//                                    name = "java-compiler-ant-tasks",
//                                    version = version,
//                                )
//                            }, {
//                                setOf(
//                                    IntelliJPluginConstants.Locations.INTELLIJ_DEPENDENCIES_REPOSITORY,
//                                ).map(::mavenRepository)
//                            }, true).takeIf { it.isNotEmpty() }
//                        }
//
//                        listOf(
//                            {
//                                runCatching { downloadCompiler(compilerVersion) }.fold(
//                                    onSuccess = { it },
//                                    onFailure = {
//                                        warn(taskContext, "Cannot resolve java-compiler-ant-tasks in version: $compilerVersion")
//                                        null
//                                    },
//                                )
//                            },
//                            {
//                                /**
//                                 * Try falling back on the version without the -EAP-SNAPSHOT suffix if the download
//                                 * for it fails - not all versions have a corresponding -EAP-SNAPSHOT version present
//                                 * in the snapshot repository.
//                                 */
//                                if (compilerVersion.endsWith(IntelliJPluginConstants.RELEASE_SUFFIX_EAP)) {
//                                    val nonEapVersion = compilerVersion.replace(IntelliJPluginConstants.RELEASE_SUFFIX_EAP, "")
//                                    runCatching { downloadCompiler(nonEapVersion) }.fold(
//                                        onSuccess = {
//                                            warn(taskContext, "Resolved non-EAP java-compiler-ant-tasks version: $nonEapVersion")
//                                            it
//                                        },
//                                        onFailure = {
//                                            warn(taskContext, "Cannot resolve java-compiler-ant-tasks in version: $nonEapVersion")
//                                            null
//                                        },
//                                    )
//                                } else {
//                                    null
//                                }
//                            },
//                            {
//                                /**
//                                 * Get the list of available packages and pick the closest lower one.
//                                 */
//                                val closestCompilerVersion = URL(IntelliJPluginConstants.JAVA_COMPILER_ANT_TASKS_MAVEN_METADATA).openStream().use { inputStream ->
//                                    val version = compilerVersion.toVersion()
//                                    XmlExtractor<MavenMetadata>().unmarshal(inputStream).versioning?.versions
//                                        ?.map(Version.Companion::parse)?.filter { it <= version }
//                                        ?.maxOf { it }
//                                        ?.version
//                                }
//
//                                if (closestCompilerVersion == null) {
//                                    warn(taskContext, "Cannot resolve java-compiler-ant-tasks Maven metadata")
//                                    null
//                                } else {
//                                    runCatching { downloadCompiler(closestCompilerVersion) }.fold(
//                                        onSuccess = {
//                                            warn(taskContext, "Resolved closest lower java-compiler-ant-tasks version: $closestCompilerVersion")
//                                            it
//                                        },
//                                        onFailure = {
//                                            warn(taskContext, "Cannot resolve java-compiler-ant-tasks in version: $closestCompilerVersion")
//                                            null
//                                        },
//                                    )
//                                }
//                            },
//                        ).asSequence().mapNotNull { it() }.firstOrNull().orEmpty()
//                    } else {
//                        warn(
//                            taskContext,
//                            "Compiler in '$compilerVersion' version can't be resolved from Maven. Minimal version supported: 2018.3+. Use higher 'intellij.version' or specify the 'compilerVersion' property manually.",
//                        )
//                        emptyList()
//                    }
//                })
//
//                outputDir.convention(project.layout.buildDirectory.map { it.dir("instrumented").dir(name) })
//                instrumentationLogs.convention(project.gradle.startParameter.logLevel == LogLevel.INFO)
//
//                dependsOn(sourceSet.classesTaskName)
//                finalizedBy(IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME)
//                onlyIf { instrumentCodeProvider.get() }
//            }
//
//            // Ensure that our task is invoked when the source set is built
//            sourceSet.compiledBy(instrumentTaskProvider)
//        }
//
//        val instrumentTaskProvider = project.tasks.named<InstrumentCodeTask>(IntelliJPluginConstants.INSTRUMENT_CODE_TASK_NAME)
//        val instrumentedJarTaskProvider = project.tasks.register<InstrumentedJarTask>(IntelliJPluginConstants.Tasks.INSTRUMENTED_JAR) {
//            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//
//            archiveBaseName.convention(jarTaskProvider.flatMap { jarTask ->
//                jarTask.archiveBaseName.map {
//                    "${IntelliJPluginConstants.INSTRUMENTED_JAR_PREFIX}-$it"
//                }
//            })
//
//            from(instrumentTaskProvider)
//            with(jarTaskProvider.get())
//
//            dependsOn(instrumentTaskProvider)
//
//            onlyIf { instrumentCodeProvider.get() }
//        }
//
//        project.artifacts.add(instrumentedJar.name, instrumentedJarTaskProvider)
//    }
}
