package org.jetbrains.intellij.platform.gradle.tasks

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import java.io.ByteArrayOutputStream

/**
 * A Gradle task for generating a lexer using JFlex for the IntelliJ Platform.
 * This task takes a Flex definition file as an input, generates the lexer, and saves it in the specified output directory.
 * It also optionally purges old files and allows specifying a custom skeleton file.
 */
@CacheableTask
abstract class GenerateLexerTask : JavaExec() {

    /**
     * Required.
     * The output directory for the generated lexer.
     */
    @get:OutputDirectory
    abstract val targetOutputDir: DirectoryProperty

    /**
     * Required.
     * The source Flex file to generate the lexer from.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    /**
     * An optional path to the skeleton file to use for the generated lexer.
     * The path will be provided as `--skel` option.
     * By default, it uses the [`idea-flex.skeleton`](https://raw.github.com/JetBrains/intellij-community/master/tools/lexer/idea-flex.skeleton) skeleton file.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val skeleton: RegularFileProperty

    /**
     * Purge old files from the target directory before generating the lexer.
     */
    @get:Input
    @get:Optional
    abstract val purgeOldFiles: Property<Boolean>

    /**
     * The location of the lexer class computed from the [targetOutputDir].
     * Because the lexer class is defined in the flex file, it needs to be passed here too.
     */
    fun targetFile(lexerClass: String): Provider<RegularFile> = targetOutputDir.file("${lexerClass}.java")

    /**
     * The location of the lexer class computed from the [targetOutputDir].
     * Because the lexer class is defined in the flex file, it needs to be passed here too.
     */
    fun targetFile(lexerClass: Provider<String>): Provider<RegularFile> = lexerClass.flatMap(::targetFile)

    @TaskAction
    override fun exec() {
        if (purgeOldFiles.orNull == true) {
            targetOutputDir.asFile.get().deleteRecursively()
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

    private fun getArguments(): List<String> {
        val args = mutableListOf(
            "-d", targetOutputDir.asPath.safePathString,
        )

        if (skeleton.isPresent) {
            args.add("--skel")
            args.add(skeleton.asPath.safePathString)
        }

        args.add(sourceFile.asPath.safePathString)

        return args
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Generate lexer for IntelliJ Platform Grammar Kit"
        mainClass = "jflex.Main"
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<GenerateLexerTask>(Tasks.GENERATE_LEXER) {
                val intellijPlatformJFlexConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_JFLEX]

                classpath += intellijPlatformJFlexConfiguration
            }
    }
}