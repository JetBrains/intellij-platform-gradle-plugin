// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.aware.SigningAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Validates the signature of the plugin archive file using the [Marketplace ZIP Signer](https://github.com/JetBrains/marketplace-zip-signer) library.
 *
 * For more details, see [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) article.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-signing.html">Plugin Signing</a>
 * @see <a href="https://github.com/JetBrains/marketplace-zip-signer">Marketplace ZIP Signer</a>
 */
@CacheableTask
abstract class VerifyPluginSignatureTask : JavaExec(), SigningAware {

    /**
     * Input, unsigned ZIP archive file.
     * Refers to `in` CLI option.
     */
    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputArchiveFile: RegularFileProperty

    /**
     * A string containing X509 certificates.
     * The first certificate from the chain will be used as a certificate authority (CA).
     * Refers to `cert` CLI option.
     *
    Takes precedence over the [certificateChainFile] property.
     *
     */
    @get:Input
    @get:Optional
    abstract val certificateChain: Property<String>

    /**
     * Path to the file containing X509 certificates.
     * The first certificate from the chain will be used as a certificate authority (CA).
     * Refers to `cert-file` CLI option.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val certificateChainFile: RegularFileProperty

    private val log = Logger(javaClass)

    @TaskAction
    override fun exec() {
        val cliPath = zipSignerExecutable.asPath

        log.debug("Marketplace ZIP Signer CLI path: $cliPath")

        classpath = objectFactory.fileCollection().from(cliPath)
        args(arguments.toList())

        super.exec()
    }

    /**
     * Collects all the options for the Plugin Verifier CLI provided with the task configuration.
     *
     * @return array with all available CLI options
     * @throws InvalidUserDataException
     */
    private val arguments = sequence {
        val file = inputArchiveFile.orNull?.let { regularFile ->
            requireNotNull(regularFile.asPath.takeIf { it.exists() }) { "Plugin file does not exist: $regularFile" }
        }
        requireNotNull(file) { "Input archive file is not provided." }

        log.debug("Distribution file: $file")

        yield("-in")
        yield(file.pathString)

        certificateChain.orNull?.let {
            yield("-cert")
            yield(createTemporaryCertificateChainFile(it).pathString)

            yield(
                Base64.getDecoder()
                    .runCatching { decode(it.trim()).let(::String) }
                    .getOrDefault(it)
            )

            log.debug("Using certificate chain passed as content")
        }
            ?: certificateChainFile.orNull?.let {
                yield("-cert")
                yield(it.asPath.pathString)
                log.debug("Using certificate chain passed as file")
            }
            ?: throw InvalidUserDataException("Certificate chain not found. One of the 'certificateChain' or 'certificateChainFile' properties has to be provided.")
    }

    private fun createTemporaryCertificateChainFile(content: String) =
        temporaryDir.resolve("certificate-chain.pem")
            .also { it.writeText(content) }
            .toPath()

    init {
        group = Plugin.GROUP_NAME
        description = "Verifies signed ZIP archive with the provided key using marketplace-zip-signer library."

        mainClass.set("org.jetbrains.zip.signer.ZipSigningTool")
        args = listOf("verify")
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<VerifyPluginSignatureTask>(Tasks.VERIFY_PLUGIN_SIGNATURE) {
                val projectNameProvider = project.extensionProvider.flatMap { it.projectName }
                val versionProvider = project.extensionProvider.flatMap { it.pluginConfiguration.version }
                val signingProvider = project.extensionProvider.map { it.signing }

                val inputProvider =
                    project.layout.buildDirectory.flatMap {
                        projectNameProvider.zip(versionProvider) { name, version ->
                            it.file("distributions/${name}-${version}-signed.zip")
                        }
                    }

                inputArchiveFile.convention(inputProvider)
                certificateChain.convention(signingProvider.flatMap { it.certificateChain })
                certificateChainFile.convention(signingProvider.flatMap { it.certificateChainFile })
            }
    }
}
