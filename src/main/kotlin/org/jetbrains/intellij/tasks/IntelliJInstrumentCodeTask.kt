// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("SameReturnValue")

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.createParentDirs
import com.jetbrains.plugin.structure.base.utils.deleteLogged
import com.jetbrains.plugin.structure.base.utils.exists
import com.jetbrains.plugin.structure.base.utils.isDirectory
import groovy.lang.Closure
import org.gradle.api.file.FileType
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.tooling.BuildException
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.info
import org.jetbrains.intellij.logCategory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

open class IntelliJInstrumentCodeTask @Inject constructor(
    objectFactory: ObjectFactory,
) : ConventionTask() {

    companion object {
        const val FILTER_ANNOTATION_REGEXP_CLASS = "com.intellij.ant.ClassFilterAnnotationRegexp"
        const val LOADER_REF = "java2.loader"
    }

    @get:Internal
    val sourceSetCompileClasspath = objectFactory.fileCollection()

    @get:Input
    @get:Optional
    val ideaDependency = objectFactory.property<IdeaDependency>()

    @get:Input
    @get:Optional
    val javac2 = objectFactory.property<File>()

    @get:Input
    val compilerVersion = objectFactory.property<String>()

    @get:Incremental
    @get:InputFiles
    val classesDirs = objectFactory.fileCollection()

    @get:Incremental
    @get:InputFiles
    val formsDirs = objectFactory.fileCollection()

    @get:Internal
    val sourceDirs = objectFactory.fileCollection()

    @get:OutputDirectory
    val outputDir = objectFactory.directoryProperty()

    @get:Input
    val compilerClassPathFromMaven = objectFactory.listProperty<File>()

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
        val temporaryDirPath = temporaryDir.also {
            it.mkdirs()
        }.toPath()

        inputChanges.getFileChanges(formsDirs).forEach { change ->
            val path = change.file.toPath()
            val sourceDir = sourceDirs.find { sourceDir ->
                path.startsWith(sourceDir.toPath())
            }?.toPath() ?: return@forEach
            val relativePath = sourceDir.relativize(path)

            val compiledClassRelativePath = relativePath.toString().replace(".form", ".class")
            val compiledClassPath = outputDirPath.resolve(compiledClassRelativePath).takeIf { it.exists() } ?: return@forEach
            val instrumentedClassPath = temporaryDirPath.resolve(compiledClassRelativePath)
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
                .forEach { file ->
                    val relativePath = temporaryDirPath.relativize(file)
                    outputDirPath.resolve(relativePath).apply {
                        createParentDirs()
                        Files.copy(file, this, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
        }
    }

    // local compiler
    private fun compilerClassPath() = javac2.orNull?.let {
        it.takeIf(File::exists)?.let { file ->
            File("${ideaDependency.get().classes}/lib").listFiles { _, name ->
                listOf(
                    "jdom.jar",
                    "asm-all.jar",
                    "asm-all-*.jar",
                    "jgoodies-forms.jar",
                    "forms-*.jar",
                ).any { pattern ->
                    val parts = pattern.split('*')
                    name.startsWith(parts.first()) && name.endsWith(parts.last())
                }
            }.orEmpty().filterNotNull() + file
        }
    } ?: compilerClassPathFromMaven.get()

    private fun prepareNotNullInstrumenting(classpath: List<File>): Boolean {
        try {
            ant.invokeMethod(
                "typedef",
                mapOf(
                    "name" to "skip",
                    "classpath" to classpath.joinToString(":"),
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
