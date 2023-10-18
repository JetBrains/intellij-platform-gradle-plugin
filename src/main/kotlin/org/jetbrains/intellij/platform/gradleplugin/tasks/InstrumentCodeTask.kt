// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("SameReturnValue")

package org.jetbrains.intellij.platform.gradleplugin.tasks

import com.jetbrains.plugin.structure.base.utils.*
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
import org.jetbrains.intellij.platform.gradleplugin.*
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradleplugin.dependency.IdeaDependency
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Deprecated("Use 'InstrumentCodeTask' instead", ReplaceWith("InstrumentCodeTask"), DeprecationLevel.ERROR)
typealias IntelliJInstrumentCodeTask = InstrumentCodeTask

/**
 * The following attributes help you to tune instrumenting behaviour in `instrumentCode { ... }` block.
 */
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
                it.createDir()
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
                    it
                        .exists()
                        .ifFalse(it::create)
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
                    createParentDirs()
                    Files.copy(path, this, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        instrumentCode(instrumentNotNull) {
            Files.walk(temporaryDirPath)
                .filter { !it.isDirectory }
                .forEach { path ->
                    val relativePath = temporaryDirPath.relativize(path)
                    outputDirPath.resolve(relativePath).apply {
                        createParentDirs()
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
            ideaDependency.get().classes.toPath().resolve("lib").listFiles().filter {
                listOf(
                    "jdom.jar",
                    "asm-all.jar",
                    "asm-all-*.jar",
                    "jgoodies-forms.jar",
                    "forms-*.jar",
                ).any { pattern ->
                    val (first, last) = pattern.split('*') + listOf("")
                    it.simpleName.startsWith(first) && (last.isEmpty() || it.simpleName.endsWith(last))
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
}
