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
     * The location of the generated parser class, relative to the [targetRootOutputDir].
     * For backwards compatibility, setting this property changes the default of [purgeOldFiles] to `false`.
     * Otherwise, the value of this property is only used when [purgeOldFiles] is set to `true`.
     * When this property is set, only the given file or directory is deleted before re-generating the parser.
     */
    @Deprecated(
        message = "The property is removed without replacement. " +
                "Be aware that the task may delete all files in `targetRootOutputDir` after unsetting this property. " +
                "You may set `purgeOldFiles = false` to avoid deleting the files. " +
                "When this property and `pathToPsiRoot` is not set, `purgeOldFiles` gets enabled by default. " +
                "This property is only relevant when the directory at `targetRootOutputDir` overlaps with other files. " +
                "Note that overlapping task outputs are discouraged by Gradle and can cause issues with the build cache.",
        level = DeprecationLevel.WARNING,
    )
    @get:Input
    @get:Optional
    abstract val pathToParser: Property<String>

    /**
     * The location of the generated PSI files, relative to the [targetRootOutputDir].
     * For backwards compatibility, setting this property changes the default of [purgeOldFiles] to `false`.
     * Otherwise, the value of this property is only used when [purgeOldFiles] is set to `true`.
     * When this property is set, only the given file or directory is deleted before re-generating the parser.
     */
    @Deprecated(
        message = "The property is removed without replacement. " +
                "Be aware that the task may delete all files in `targetRootOutputDir` after unsetting this property. " +
                "You may set `purgeOldFiles = false` to avoid deleting the files. " +
                "When this property and `pathToParser` is not set, `purgeOldFiles` gets enabled by default. " +
                "This property is only relevant when the directory at `targetRootOutputDir` overlaps with other files. " +
                "Note that overlapping task outputs are discouraged by Gradle and can cause issues with the build cache.",
        level = DeprecationLevel.WARNING,
    )
    @get:Input
    @get:Optional
    abstract val pathToPsiRoot: Property<String>

    /**
     * The output parser file computed from the [pathToParser] property.
     */
    @Deprecated(
        message = "The method will be removed together with `pathToParser`.",
        replaceWith = ReplaceWith("targetRootOutputDir.file(pathToParser)"),
        level = DeprecationLevel.WARNING,
    )
    fun parserFile(): Provider<RegularFile> = targetRootOutputDir.file(pathToParser)

    /**
     * The output PSI directory computed from the [pathToPsiRoot] property.
     */
    @Deprecated(
        message = "The method will be removed together with `pathToPsiRoot`.",
        replaceWith = ReplaceWith("targetRootOutputDir.dir(pathToPsiRoot)"),
        level = DeprecationLevel.WARNING,
    )
    fun psiDir(): Provider<Directory> = targetRootOutputDir.dir(pathToPsiRoot)

    /**
     * Purge old files from the target directory before generating the parser.
     * By default, old files are purged unless [pathToParser] and [pathToPsiRoot] have been specified.
     * If you want to disable this option because the output directory is shared with another task,
     * note that you may run into issues with stale files. Also note that
     * [overlapping task outputs are discouraged by Gradle](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#avoid_overlapping_task_outputs)
     * and may cause issues when using the build cache.
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
        purgeOldFiles.orNull.also { purge ->
            if (purge == false) {
                // Do nothing as `purgeOldFiles` is explicitly disabled
            } else if (!pathToParser.isPresent && !pathToPsiRoot.isPresent) {
                targetRootOutputDir.get().asFile.deleteRecursively()
            } else if (purge == true) {
                // Delete only the directories specified by `pathToParser` and `pathToPsiRoot` for backwards compatibility.
                targetRootOutputDir.get().asFile.apply {
                    resolve(pathToParser.get()).deleteRecursively()
                    resolve(pathToPsiRoot.get()).deleteRecursively()
                }
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
