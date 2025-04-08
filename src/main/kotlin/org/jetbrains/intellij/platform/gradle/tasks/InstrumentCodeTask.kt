// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("SameReturnValue")

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.base.utils.deleteLogged
import groovy.lang.Closure
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.jetbrains.intellij.platform.gradle.BuildException
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import org.jetbrains.intellij.platform.gradle.tasks.aware.JavaCompilerAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.Throws
import kotlin.io.path.*

private const val FILTER_ANNOTATION_REGEXP_CLASS = "com.intellij.ant.ClassFilterAnnotationRegexp"
private const val LOADER_REF = "java2.loader"

/**
 * Executes the code instrumentation using the Ant tasks provided by the used IntelliJ Platform dependency.
 *
 * The code instrumentation scans the compiled Java and Kotlin classes for JetBrains Annotations usages to replace them with their relevant functionalities.
 *
 * The task is controlled with the [IntelliJPlatformExtension.instrumentCode] extension property, enabled by default.
 * To properly run the instrumentation, it is required to add [IntelliJPlatformDependenciesExtension.instrumentationTools] dependencies to the project.
 *
 * This dependency is available via the [IntelliJPlatformRepositoriesExtension.intellijDependencies] repository, which can be added separately
 * or using the [IntelliJPlatformRepositoriesExtension.defaultRepositories] helper.
 */
@CacheableTask
abstract class InstrumentCodeTask : DefaultTask(), JavaCompilerAware {

    /**
     * Specifies compile classpath of the project's source set.
     */
    @get:Internal
    abstract val sourceSetCompileClasspath: ConfigurableFileCollection

    /**
     * Specifies the list of directories with compiled classes.
     *
     * Default value: `classesDirs` of the project's source sets.
     */
    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classesDirs: ConfigurableFileCollection

    /**
     * Specifies the list of directories with GUI Designer form files.
     *
     * Default value: `.form` files of the project's source sets.
     */
    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val formsDirs: ConfigurableFileCollection

    /**
     * Specifies the location of the source code.
     */
    @get:Internal
    abstract val sourceDirs: ConfigurableFileCollection

    /**
     * Enables `INFO` logging when running Ant tasks.
     *
     * Default value: `false`
     */
    @get:Internal
    abstract val instrumentationLogs: Property<Boolean>

    /**
     * Specifies the output directory for instrumented classes.
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    private val log = Logger(javaClass)

    @TaskAction
    @OptIn(ExperimentalPathApi::class)
    fun instrumentCode(inputChanges: InputChanges) = runCatching {
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
                it.deleteRecursively()
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
    }.onFailure {
        val message = when (it.cause) {
            is ClassNotFoundException -> "No Java Compiler dependency found."
            is NoClassDefFoundError -> "No Java Compiler transitive dependencies found."
            else -> throw it
        }

        throw GradleException(
            """
            $message
            Please ensure the `instrumentationTools()` entry is present in the project dependencies section along with the `intellijDependencies()` entry in the repositories section.
            See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
            """.trimIndent(),
            it,
        )
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

    init {
        group = Plugin.GROUP_NAME
        description = "Executes the code instrumentation."
    }

    companion object : Registrable {
        override fun register(project: Project) {
            val instrumentCodeEnabled = project.extensionProvider.flatMap { it.instrumentCode }
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
                }
            }
        }
    }
}
