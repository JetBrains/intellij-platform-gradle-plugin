// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("SameReturnValue")

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.base.utils.deleteLogged
import com.jetbrains.plugin.structure.base.utils.deleteQuietly
import groovy.lang.Closure
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileType
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.jetbrains.intellij.platform.gradle.BuildException
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.INSTRUMENT_CODE
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.aware.JavaCompilerAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory

private const val FILTER_ANNOTATION_REGEXP_CLASS = "com.intellij.ant.ClassFilterAnnotationRegexp"
private const val LOADER_REF = "java2.loader"

@CacheableTask
abstract class InstrumentCodeTask : DefaultTask(), JavaCompilerAware {

    /**
     * Compile classpath of the project's source set.
     */
    @get:Internal
    abstract val sourceSetCompileClasspath: ConfigurableFileCollection

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
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    private val log = Logger(javaClass)

    init {
        group = Plugin.GROUP_NAME
        description = "Code instrumentation task."
    }

    @TaskAction
    fun instrumentCode(inputChanges: InputChanges) {
        ant.invokeMethod(
            "taskdef",
            mapOf(
                "name" to "instrumentIdeaExtensions",
                "classpath" to javaCompilerConfiguration.joinToString(":"),
                "loaderref" to LOADER_REF,
                "classname" to "com.intellij.ant.InstrumentIdeaExtensions",
            ),
        )

        log.info("Compiling forms and instrumenting code with nullability preconditions")
        val instrumentNotNull = prepareNotNullInstrumenting()

        val outputDirPath = outputDirectory.asPath
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

//                    val source = classesDirs.firstNotNullOfOrNull { classesDir ->
//                        classesDir
//                            .resolve(relativePath.pathString.substringBefore('$').removeSuffix(".class") + ".class")
//                            .toPath()
//                            .takeIf { it.exists() }
//                    }
//                    source?.apply {
//                        parent.createDirectories()
//                        Files.copy(path, this, StandardCopyOption.REPLACE_EXISTING)
//                    }
                }
        }
    }

    /**
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun prepareNotNullInstrumenting() = runCatching {
        ant.invokeMethod(
            "typedef",
            mapOf(
                "name" to "skip",
                "classpath" to javaCompilerConfiguration.joinToString(":"),
                "loaderref" to LOADER_REF,
                "classname" to FILTER_ANNOTATION_REGEXP_CLASS
            )
        )
        true
    }.getOrElse { e: Throwable ->
        if (e is BuildException && e.cause is ClassNotFoundException && e.cause?.message == FILTER_ANNOTATION_REGEXP_CLASS) {
            log.info("Old version of Javac2 is used, instrumenting code with nullability will be skipped.")
            false
        } else {
            throw e
        }
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

    companion object : Registrable {
        override fun register(project: Project) {
            val extension = project.the<IntelliJPlatformExtension>()
            val instrumentCodeEnabled = extension.instrumentCode
            val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer

            sourceSets.forEach { sourceSet ->
                val name = sourceSet.getTaskName("instrument", "code")

                project.registerTask<InstrumentCodeTask>(name, configureWithType = false) {
                    outputDirectory.convention(project.layout.buildDirectory.map { it.dir("instrumented").dir(name) })
                    instrumentationLogs.convention(project.gradle.startParameter.logLevel == LogLevel.INFO)

                    sourceDirs.from(project.provider {
                        sourceSet.allJava.srcDirs
                    })
                    formsDirs.from(project.provider {
                        sourceDirs.asFileTree.filter {
                            it.toPath().extension == "form"
                        }
                    })
                    classesDirs.from(project.provider {
                        (sourceSet.output.classesDirs as ConfigurableFileCollection).from.run {
                            project.files(this).filter { it.exists() }
                        }
                    })

                    sourceSetCompileClasspath.from(project.provider {
                        sourceSet.compileClasspath
                    })

                    dependsOn(sourceSet.classesTaskName)
                    onlyIf { instrumentCodeEnabled.get() }
                    sourceSet.compiledBy(this)

                    // finalizedBy(IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME)
                }
            }

            val jarTaskProvider = project.tasks.named<Jar>(Tasks.External.JAR)
            val instrumentCodeTaskProvider = project.tasks.named<InstrumentCodeTask>(INSTRUMENT_CODE)
            val runtimeElementsConfiguration = project.configurations[Configurations.External.RUNTIME_ELEMENTS]

            project.registerTask<Jar>(Tasks.INSTRUMENTED_JAR, configureWithType = false) {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                archiveClassifier.convention("instrumented")

                from(instrumentCodeTaskProvider)
                with(jarTaskProvider.get())


                onlyIf { instrumentCodeEnabled.get() }
            }

            val instrumentedJarTaskProvider = project.tasks.named<Jar>(Tasks.INSTRUMENTED_JAR)

            runtimeElementsConfiguration.artifacts.removeIf {
                it.file == jarTaskProvider.flatMap { task -> task.archiveFile }.asPath.toFile()
            }
            project.artifacts.add(runtimeElementsConfiguration.name, instrumentCodeEnabled.flatMap { enabled ->
                when {
                    enabled -> instrumentedJarTaskProvider
                    else -> jarTaskProvider
                }
            })
        }
    }
}
