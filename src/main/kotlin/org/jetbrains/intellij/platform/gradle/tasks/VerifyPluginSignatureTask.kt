// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.asPath
import org.jetbrains.intellij.platform.gradle.debug
import org.jetbrains.intellij.platform.gradle.logCategory
import org.jetbrains.intellij.platform.gradle.tasks.base.ZipSigningToolBase
import javax.inject.Inject

/**
 * Validates the signature of the plugin archive file using [Marketplace ZIP Signer](https://github.com/JetBrains/marketplace-zip-signer) library.
 *
 * For more details, see [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) article.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-signing.html">Plugin Signing</a>
 * @see <a href="https://github.com/JetBrains/marketplace-zip-signer">Marketplace ZIP Signer</a>
 */
@Deprecated(message = "CHECK")
@CacheableTask
abstract class VerifyPluginSignatureTask @Inject constructor(
    objectFactory: ObjectFactory,
    execOperations: ExecOperations,
) : ZipSigningToolBase(objectFactory, execOperations) {

    /**
     * Path to the file containing X509 certificates.
     * The first certificate from the chain will be used as a certificate authority (CA).
     * Refers to `cert-file` CLI option.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val certificateChainFile: RegularFileProperty

    private val context = logCategory()

    init {
        group = PLUGIN_GROUP_NAME
        description = "Verifies signed ZIP archive with the provided key using marketplace-zip-signer library."
    }

    @TaskAction
    fun verifyPluginSignature() {
        executeZipSigningTool("verify")
    }

    /**
     * Collects all the options for the Plugin Verifier CLI provided with the task configuration.
     *
     * @return array with all available CLI options
     */
    override fun collectArguments(): List<String> {
        val arguments = mutableListOf<String>()

        certificateChainFile.orNull?.let {
            arguments.add("-cert")
            arguments.add(it.asPath.toAbsolutePath().toString())

            debug(context, "Using certificate chain passed as file")
        } ?: throw InvalidUserDataException("Certificate chain not found.")

        return arguments + super.collectArguments()
    }
}
