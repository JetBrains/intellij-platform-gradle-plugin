// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks

import com.jetbrains.plugin.structure.base.utils.exists
import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.process.internal.ExecException
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradleplugin.asPath
import org.jetbrains.intellij.platform.gradleplugin.debug
import org.jetbrains.intellij.platform.gradleplugin.error
import org.jetbrains.intellij.platform.gradleplugin.logCategory
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Base class for tasks that use Marketplace ZIP Signer CLI.
 */
@CacheableTask
abstract class ZipSigningToolBase(
    private val objectFactory: ObjectFactory,
    private val execOperations: ExecOperations,
) : DefaultTask() {

    /**
     * Input, unsigned ZIP archive file.
     * Refers to `in` CLI option.
     */
    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputArchiveFile: RegularFileProperty

    /**
     * Local path to the Marketplace ZIP Signer CLI that will be used.
     */
    @get:Input
    @get:Optional
    abstract val cliPath: Property<String>

    private val context = logCategory()

    init {
        group = PLUGIN_GROUP_NAME
        description = "Signs the ZIP archive with the provided key using marketplace-zip-signer library."
    }

    protected fun executeZipSigningTool(command: String) {
        val cliPath = resolveCliPath()
        val cliArgs = listOf(command) + collectArguments()

        debug(context, "Marketplace ZIP Signer CLI path: $cliPath")

        ByteArrayOutputStream().use { os ->
            try {
                execOperations.javaexec {
                    classpath = objectFactory.fileCollection().from(cliPath)
                    mainClass.set("org.jetbrains.zip.signer.ZipSigningTool")
                    args = cliArgs
                    standardOutput = TeeOutputStream(System.out, os)
                    errorOutput = TeeOutputStream(System.err, os)
                }
            } catch (e: ExecException) {
                error(context, "Error during Marketplace ZIP Signer CLI execution:\n$os")
                throw e
            }
        }
    }

    /**
     * Resolves the path to the IntelliJ Marketplace ZIP Signer.
     * At first, checks if it was provided with [cliPath].
     * Fetches Marketplace ZIP Signer artifact from the GitHub Releases and resolves the path to `zip-signer-cli` jar file.
     *
     * @return path to zip-signer-cli jar
     */
    private fun resolveCliPath(): String {
        val path = cliPath.orNull
        if (!path.isNullOrEmpty()) {
            val verifier = Path.of(path)
            if (verifier.exists()) {
                return path
            }
        }

        throw InvalidUserDataException("Provided Marketplace ZIP Signer path doesn't exist.")
    }

    /**
     * Collects all the options for the Plugin Verifier CLI provided with the task configuration.
     *
     * @return array with all available CLI options
     */
    protected open fun collectArguments(): List<String> {
        val file = inputArchiveFile.orNull
            ?.run {
                asPath
                    .takeIf { it.exists() }
                    ?: throw InvalidUserDataException("Plugin file does not exist: $this")
            } ?: throw InvalidUserDataException("Input archive file is not provided.")

        debug(context, "Distribution file: $file")

        return mutableListOf(
            "-in", file.absolutePathString(),
        )
    }
}
