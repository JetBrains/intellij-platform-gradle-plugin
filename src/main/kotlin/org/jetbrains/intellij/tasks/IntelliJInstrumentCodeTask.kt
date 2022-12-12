// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("SameReturnValue")

package org.jetbrains.intellij.tasks

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
import org.jetbrains.intellij.*
import org.jetbrains.intellij.dependency.IdeaDependency
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

abstract class IntelliJInstrumentCodeTask : DefaultTask() {

    companion object {
        const val FILTER_ANNOTATION_REGEXP_CLASS = "com.intellij.ant.ClassFilterAnnotationRegexp"
        const val LOADER_REF = "java2.loader"
    }

    @get:Internal
    abstract val sourceSetCompileClasspath: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val ideaDependency: Property<IdeaDependency>

    @get:Input
    @get:Optional
    abstract val javac2: Property<File>

    @get:Input
    abstract val compilerVersion: Property<String>

    @get:Incremental
    @get:InputFiles
    abstract val classesDirs: ConfigurableFileCollection

    @get:Incremental
    @get:InputFiles
    abstract val formsDirs: ConfigurableFileCollection

    @get:Internal
    abstract val sourceDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val compilerClassPathFromMaven: ListProperty<File>

    private val context = logCategory()

    @TaskAction
    fun instrumentClasses(inputChanges: InputChanges) {
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

        val outputDirPath = outputDir.get().asFile.toPath()
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
                    val (first, last) = pattern.split('*')
                    it.simpleName.startsWith(first) && it.simpleName.endsWith(last)
                }
            } + path
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
                info(
                    context,
                    "Old version of Javac2 is used, instrumenting code with nullability will be skipped. " +
                            "Use IDEA >14 SDK (139.*) to fix this",
                )
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
                // to enable debug mode, use:
                // ant.lifecycleLogLevel = org.gradle.api.AntBuilder.AntMessagePriority.DEBUG

                ant.invokeMethod("instrumentIdeaExtensions", arrayOf(
                    mapOf(
                        "srcdir" to dirs.joinToString(":"),
                        "destdir" to temporaryDir,
                        "classpath" to sourceSetCompileClasspath.joinToString(":"),
                        "includeantruntime" to false,
                        "instrumentNotNull" to instrumentNotNull
                    ),
                    object : Closure<Any>(this, this) {
                        @Suppress("unused") // Groovy calls using reflection inside of Closure
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
}
