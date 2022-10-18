/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.intellij.tasks

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.utils.ExternalToolRunner
import org.jetbrains.intellij.utils.cliArg
import org.jetbrains.intellij.utils.ioFile
import org.jetbrains.intellij.utils.jvmToolFile
import org.jetbrains.intellij.utils.mangledName
import org.jetbrains.intellij.utils.normalizedPath
import org.jetbrains.intellij.utils.notNullProperty
import org.jetbrains.intellij.utils.nullableProperty
import java.io.File
import java.io.Writer

// TODO: generalize some options e.g. defaultComposeRulesFile should just be defaultRulesFile
abstract class AbstractProguardTask : AbstractTask() {

    @get:Optional
    @get:InputFiles
    val inputFiles: ConfigurableFileCollection = objects.fileCollection()

    @get:InputFiles
    val libraryJars: ConfigurableFileCollection = objects.fileCollection()

    @get:InputFile
    val mainJar: RegularFileProperty = objects.fileProperty()

    @get:Internal
    internal val mainJarInDestinationDir: Provider<RegularFile> = mainJar.flatMap {
        destinationDir.file(it.asFile.name)
    }

    @get:InputFiles
    val configurationFiles: ConfigurableFileCollection = objects.fileCollection()

    @get:Optional
    @get:Input
    val dontobfuscate: Property<Boolean?> = objects.nullableProperty()

    // todo: DSL for excluding default rules
    // also consider pulling coroutines rules from coroutines artifact
    // https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/resources/META-INF/proguard/coroutines.pro
    @get:Optional
    @get:InputFile
    val defaultComposeRulesFile: RegularFileProperty = objects.fileProperty()

    @get:Input
    val proguardVersion: Property<String> = objects.notNullProperty()

    @get:Input
    val javaHome: Property<String> = objects.notNullProperty(System.getProperty("java.home"))

    @get:Optional
    @get:Input
    val mainClass: Property<String?> = objects.nullableProperty()

    @get:Internal
    val maxHeapSize: Property<String?> = objects.nullableProperty()

    @get:OutputDirectory
    val destinationDir: DirectoryProperty = objects.directoryProperty()

    @get:LocalState
    protected val workingDir: Provider<Directory> = project.layout.buildDirectory.dir("compose/tmp/$name")

    private val rootConfigurationFile = workingDir.map { it.file("root-config.pro") }

    private val jarsConfigurationFile = workingDir.map { it.file("jars-config.pro") }

    @TaskAction
    fun execute() {
        val javaHome = File(javaHome.get())
        val proguardFiles = project.configurations.detachedConfiguration(
            project.dependencies.create("com.guardsquare:proguard-gradle:${proguardVersion.get()}")
        ).files

        cleanDirs(destinationDir, workingDir)
        val destinationDir = destinationDir.ioFile.absoluteFile

        // todo: can be cached for a jdk
        val jmods = javaHome.resolve("jmods").walk().filter {
            it.isFile && it.path.endsWith("jmod", ignoreCase = true)
        }.toList()

        val inputToOutputJars = LinkedHashMap<File, File>()
        // avoid mangling mainJar
        inputToOutputJars[mainJar.ioFile] = mainJarInDestinationDir.ioFile
        for (inputFile in inputFiles) {
            if (inputFile.name.endsWith(".jar", ignoreCase = true)) {
                inputToOutputJars.putIfAbsent(inputFile, destinationDir.resolve(inputFile.mangledName()))
            } else {
                inputFile.copyTo(destinationDir.resolve(inputFile.name))
            }
        }

        jarsConfigurationFile.ioFile.bufferedWriter().use { writer ->
            for ((input, output) in inputToOutputJars.entries) {
                writer.writeLn("-injars '${input.normalizedPath()}'")
                writer.writeLn("-outjars '${output.normalizedPath()}'")
            }

            for (jmod in jmods) {
                writer.writeLn("-libraryjars '${jmod.normalizedPath()}'(!**.jar;!module-info.class)")
            }
            for (libraryJar in libraryJars) {
                writer.writeLn("-libraryjars '${libraryJar.canonicalPath}'")
            }
            // TODO: do this here or in the IntelliJPlugin?
            val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer
            sourceSets.getByName("main").compileClasspath.forEach {
                writer.writeLn("-libraryjars '${it.canonicalPath}'")
            }
        }

        rootConfigurationFile.ioFile.bufferedWriter().use { writer ->
            if (dontobfuscate.orNull == true) {
                writer.writeLn("-dontobfuscate")
            }

            if (mainClass.isPresent) {
                writer.writeLn("""
                    -keep public class ${mainClass.get()} {
                        public static void main(java.lang.String[]);
                    }
                """.trimIndent())
            }

            val includeFiles = sequenceOf(
                jarsConfigurationFile.ioFile,
                defaultComposeRulesFile.ioFile
            ) + configurationFiles.files.asSequence()
            for (configFile in includeFiles.filterNotNull()) {
                writer.writeLn("-include '${configFile.normalizedPath()}'")
            }
        }

        val javaBinary = jvmToolFile(toolName = "java", javaHome = javaHome)
        val args = arrayListOf<String>().apply {
            val maxHeapSize = maxHeapSize.orNull
            if (maxHeapSize != null) {
                add("-Xmx:$maxHeapSize")
            }
            cliArg("-cp", proguardFiles.map { it.normalizedPath() }.joinToString(File.pathSeparator))
            add("proguard.ProGuard")
            // todo: consider separate flag
            cliArg("-verbose", verbose)
            cliArg("-include", rootConfigurationFile)
        }

        runExternalTool(
            tool = javaBinary,
            args = args,
            environment = emptyMap(),
            logToConsole = ExternalToolRunner.LogToConsole.Always
        ).assertNormalExitValue()
    }

    private fun Writer.writeLn(s: String) {
        write(s)
        write("\n")
    }
}