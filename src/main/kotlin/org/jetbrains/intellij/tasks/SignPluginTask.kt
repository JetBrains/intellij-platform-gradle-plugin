// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

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
import org.jetbrains.intellij.asPath
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.error
import org.jetbrains.intellij.logCategory
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.inject.Inject

@CacheableTask
abstract class SignPluginTask @Inject constructor(
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
     * Output, signed ZIP archive file.
     * Refers to `out` CLI option.
     *
     * Predefined with the name of the ZIP archive file with `-signed` name suffix attached.
     */
    @get:OutputFile
    abstract val outputArchiveFile: RegularFileProperty

    /**
     * Local path to the Marketplace ZIP Signer CLI that will be used.
     */
    @get:Input
    @get:Optional
    abstract val cliPath: Property<String>

    /**
     * KeyStore file path.
     * Refers to `ks` CLI option.
     */
    @get:Input
    @get:Optional
    abstract val keyStore: Property<String>

    /**
     * KeyStore password.
     * Refers to `ks-pass` CLI option.
     */
    @get:Input
    @get:Optional
    abstract val keyStorePassword: Property<String>

    /**
     * KeyStore key alias.
     * Refers to `ks-key-alias` CLI option.
     */
    @get:Input
    @get:Optional
    abstract val keyStoreKeyAlias: Property<String>

    /**
     * KeyStore type.
     * Refers to `ks-type` CLI option.
     */
    @get:Input
    @get:Optional
    abstract val keyStoreType: Property<String>

    /**
     * JCA KeyStore Provider name.
     * Refers to `ks-provider-name` CLI option.
     */
    @get:Input
    @get:Optional
    abstract val keyStoreProviderName: Property<String>

    /**
     * Encoded private key in PEM format.
     * Refers to `key` CLI option.
     */
    @get:Input
    @get:Optional
    abstract val privateKey: Property<String>

    /**
     * A file with encoded private key in PEM format.
     * Refers to `key-file` CLI option.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val privateKeyFile: RegularFileProperty

    /**
     * Password required to decrypt the private key.
     * Refers to `key-pass` CLI option.
     */
    @get:Input
    @get:Optional
    abstract val password: Property<String>

    /**
     * A string containing X509 certificates.
     * The first certificate from the chain will be used as a certificate authority (CA).
     * Refers to `cert` CLI option.
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
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val certificateChainFile: RegularFileProperty

    private val context = logCategory()

    @TaskAction
    fun signPlugin() {
        val file = inputArchiveFile.orNull?.asPath
        if (file == null || !file.exists()) {
            throw IllegalStateException("Plugin file does not exist: $file")
        }

        if (privateKey.orNull == null && privateKeyFile.orNull == null) {
            throw InvalidUserDataException(
                "Private key not found. One of the 'signPlugin.privateKey' or 'signPlugin.privateKeyFile' properties has to be provided."
            )
        }
        if (certificateChain.orNull == null && certificateChainFile.orNull == null) {
            throw InvalidUserDataException(
                "Certificate chain not found. One of the 'signPlugin.certificateChain' or 'signPlugin.certificateChainFile' properties has to be provided."
            )
        }

        val cliPath = resolveCliPath()
        val cliArgs = listOf("sign") + getOptions()

        debug(context, "Distribution file: $file")
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
    private fun getOptions(): List<String> {
        val args = mutableListOf(
            "-in", inputArchiveFile.get().asPath.toAbsolutePath().toString(),
            "-out", outputArchiveFile.get().asPath.toAbsolutePath().toString(),
        )

        privateKey.orNull?.let {
            args.add("-key")
            args.add(it)
            debug(context, "Using private key passed as content")
        } ?: run {
            args.add("-key-file")
            args.add(privateKeyFile.get().asPath.toAbsolutePath().toString())
            debug(context, "Using private key passed as file")
        }
        certificateChain.orNull?.let {
            args.add("-cert")
            args.add(it)
            debug(context, "Using certificate chain passed as content")
        } ?: run {
            args.add("-cert-file")
            args.add(certificateChainFile.get().asPath.toAbsolutePath().toString())
            debug(context, "Using certificate chain passed as file")
        }

        password.orNull?.let {
            args.add("-key-pass")
            args.add(it)
            debug(context, "Using private key password")
        }
        keyStore.orNull?.let {
            args.add("-ks")
            args.add(it)
        }
        keyStorePassword.orNull?.let {
            args.add("-ks-pass")
            args.add(it)
        }
        keyStoreKeyAlias.orNull?.let {
            args.add("-ks-key-alias")
            args.add(it)
        }
        keyStoreType.orNull?.let {
            args.add("-ks-type")
            args.add(it)
        }
        keyStoreProviderName.orNull?.let {
            args.add("-ks-provider-name")
            args.add(it)
        }

        return args
    }
}
