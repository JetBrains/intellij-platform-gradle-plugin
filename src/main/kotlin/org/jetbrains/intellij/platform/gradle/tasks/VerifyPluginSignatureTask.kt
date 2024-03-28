// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.aware.SigningAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

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

    init {
        group = Plugin.GROUP_NAME
        description = "Verifies signed ZIP archive with the provided key using marketplace-zip-signer library."

        mainClass.set("org.jetbrains.zip.signer.ZipSigningTool")
        args = listOf("verify")
    }

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
     */
    private val arguments = sequence {
        val file = inputArchiveFile.orNull
            ?.run {
                asPath
                    .takeIf { it.exists() }
                    ?: throw InvalidUserDataException("Plugin file does not exist: $this")
            } ?: throw InvalidUserDataException("Input archive file is not provided.")

        log.debug("Distribution file: $file")

        yield("-in")
        yield(file.absolutePathString())

        certificateChain.orNull?.let {
            yield("-cert")
            yield(createTemporaryCertificateChainFile(it).absolutePathString())

            yield(
                Base64.getDecoder()
                    .runCatching { decode(it.trim()).let(::String) }
                    .getOrDefault(it)
            )

            log.debug("Using certificate chain passed as content")
        }
            ?: certificateChainFile.orNull?.let {
                yield("-cert")
                yield(it.asPath.absolutePathString())
                log.debug("Using certificate chain passed as file")
            }
            ?: throw InvalidUserDataException("Certificate chain not found. One of the 'certificateChain' or 'certificateChainFile' properties has to be provided.")
    }

    private fun createTemporaryCertificateChainFile(content: String) =
        temporaryDir.resolve("certificate-chain.pem")
            .also { it.writeText(content) }
            .toPath()

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<VerifyPluginSignatureTask>(Tasks.VERIFY_PLUGIN_SIGNATURE) {
                val extension = project.the<IntelliJPlatformExtension>()
                val inputProvider =
                    project.layout.buildDirectory.flatMap {
                        extension.projectName.zip(extension.pluginConfiguration.version) { name, version ->
                            it.file("distributions/${name}-${version}-signed.zip")
                        }
                    }

                inputArchiveFile.convention(inputProvider)
                certificateChain.convention(extension.signing.certificateChain)
                certificateChainFile.convention(extension.signing.certificateChainFile)
            }
    }
}
