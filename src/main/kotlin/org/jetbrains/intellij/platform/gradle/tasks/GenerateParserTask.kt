package org.jetbrains.intellij.platform.gradle.tasks

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.ByteArrayOutputStream

/**
 * Task to generate a parser for the IntelliJ Platform Grammar Kit.
 *
 * This task runs a Java process to generate parser and PSI files using a provided BNF file.
 * It supports purging old files before generation, as well as specifying paths for outputs
 * relative to a root output directory.
 *
 * This task is part of the IntelliJ Platform plugin development process and integrates
 * with Gradle for task registration and execution.
 */
@CacheableTask
abstract class GenerateParserTask : JavaExec() {

    /**
     * Required.
     * The source BNF file to generate the parser from.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    /**
     * Required.
     * The output root directory.
     */
    @get:OutputDirectory
    abstract val targetRootOutputDir: DirectoryProperty

    /**
     * Required.
     * The location of the generated parser class, relative to the [targetRootOutputDir].
     */
    @get:Input
    abstract val pathToParser: Property<String>

    /**
     * Required.
     * The location of the generated PSI files, relative to the [targetRoot].
     */
    @get:Input
    abstract val pathToPsiRoot: Property<String>

    /**
     * The output parser file computed from the [pathToParser] property.
     */
    fun parserFile(): Provider<RegularFile> = targetRootOutputDir.file(pathToParser)

    /**
     * The output PSI directory computed from the [pathToPsiRoot] property.
     */
    fun psiDir(): Provider<Directory> = targetRootOutputDir.dir(pathToPsiRoot)

    /**
     * Purge old files from the target directory before generating the lexer.
     */
    @get:Input
    @get:Optional
    abstract val purgeOldFiles: Property<Boolean>

    @TaskAction
    override fun exec() {
        if (purgeOldFiles.orNull == true) {
            targetRootOutputDir.get().asFile.apply {
                resolve(pathToParser.get()).deleteRecursively()
                resolve(pathToPsiRoot.get()).deleteRecursively()
            }
        }
        ByteArrayOutputStream().use { os ->
            try {
                args = getArguments()
                errorOutput = TeeOutputStream(System.out, os)
                standardOutput = TeeOutputStream(System.out, os)
                super.exec()
            } catch (e: Exception) {
                throw GradleException(os.toString().trim(), e)
            }
        }
    }

    private fun getArguments() = listOf(targetRootOutputDir, sourceFile).map { it.asPath.normalize().toString() }

    init {
        group = Constants.Plugin.GROUP_NAME
        description = "Generate parser for IntelliJ Platform Grammar Kit"
        mainClass = "org.intellij.grammar.Main"
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<GenerateParserTask>(Tasks.GENERATE_PARSER) {
                val intellijPlatformGrammarKitConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_GRAMMAR_KIT]
                val intellijPlatformClasspathConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_CLASSPATH]

                classpath += intellijPlatformGrammarKitConfiguration + intellijPlatformClasspathConfiguration
            }
    }
}