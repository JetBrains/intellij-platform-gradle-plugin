package org.jetbrains.intellij.platform.gradle.tasks

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
    @get:Internal
    abstract val targetRootOutputDir: DirectoryProperty

    /**
     * The location of the generated parser class, relative to the [targetRootOutputDir].
     * Stale files are purged only from this path and [pathToPsiRoot].
     */
    @get:Input
    @get:Optional
    abstract val pathToParser: Property<String>

    /**
     * The location of the generated PSI files, relative to the [targetRootOutputDir].
     * Stale files are purged only from this path and [pathToParser].
     */
    @get:Input
    @get:Optional
    abstract val pathToPsiRoot: Property<String>

    /**
     * The output parser file computed from the [pathToParser] property.
     */
    fun parserFile() = targetRootOutputDir.file(pathToParser.map(::relativeOutputPath))

    /**
     * The output PSI directory computed from the [pathToPsiRoot] property.
     */
    fun psiDir() = targetRootOutputDir.dir(pathToPsiRoot.map(::relativeOutputPath))

    /**
     * The output parser file.
     */
    @get:OutputFile
    @get:Optional
    protected val parserOutputFile
        get() = parserFile()

    /**
     * The output PSI directory.
     */
    @get:OutputDirectory
    @get:Optional
    protected val psiOutputDir
        get() = psiDir()

    /**
     * Purge old parser and PSI files before generating the parser.
     * Purging requires [pathToParser] and [pathToPsiRoot] to be set, so that only generated outputs are removed.
     */
    @get:Input
    @get:Optional
    abstract val purgeOldFiles: Property<Boolean>

    @TaskAction
    override fun exec() {
        if (pathToParser.isPresent && !pathToPsiRoot.isPresent) {
            throw GradleException("'pathToParser' has a configured value, but 'pathToPsiRoot' has not. You must either remove or keep both properties.")
        } else if (pathToPsiRoot.isPresent && !pathToParser.isPresent) {
            throw GradleException("'pathToPsiRoot' has a configured value, but 'pathToParser' has not. You must either remove or keep both properties.")
        }
        purgeOldFiles()
        execWithTeeOutput(getArguments()) {
            super.exec()
        }
    }

    private fun getArguments() = listOf(targetRootOutputDir, sourceFile).map { it.asPath.normalize().toString() }

    private fun purgeOldFiles() {
        if (purgeOldFiles.orNull == false) {
            return
        }
        if (pathToParser.isPresent && pathToPsiRoot.isPresent) {
            parserOutputFile.get().asFile.deleteRecursively()
            psiOutputDir.get().asFile.deleteRecursively()
        } else {
            logger.warn("Cannot purge old parser files for $path because `pathToParser` and `pathToPsiRoot` are not set.")
        }
    }

    private fun relativeOutputPath(path: String) = path.trimStart('/', '\\')

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

                targetRootOutputDir.convention(project.layout.buildDirectory.dir("generated/sources/grammarkit-parser/java/main"))
                classpath += intellijPlatformGrammarKitConfiguration + intellijPlatformClasspathConfiguration
            }
    }
}
