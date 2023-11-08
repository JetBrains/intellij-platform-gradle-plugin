// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.process.ExecOperations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.asPath
import org.jetbrains.intellij.platform.gradle.debug
import org.jetbrains.intellij.platform.gradle.logCategory
import org.jetbrains.intellij.platform.gradle.tasks.base.ZipSigningToolBase
import java.util.*
import javax.inject.Inject
import kotlin.io.path.pathString

/**
 * Signs the ZIP archive with the provided key using [Marketplace ZIP Signer](https://github.com/JetBrains/marketplace-zip-signer) library.
 *
 * To sign the plugin before publishing to [JetBrains Marketplace](https://plugins.jetbrains.com) with the [SignPluginTask] task, it is required to provide a certificate chain and a private key with its password using `signPlugin { ... }` Plugin Signing DSL.
 *
 * As soon as [privateKey] (or [privateKeyFile]) and [certificateChain] (or [certificateChainFile]) properties are specified, the task will be executed automatically right before the [PublishPluginTask] task.
 *
 * For more details, see [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) article.
 *
 * @see [PublishPluginTask]
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-signing.html">Plugin Signing</a>
 * @see <a href="https://github.com/JetBrains/marketplace-zip-signer">Marketplace ZIP Signer</a>
 */
@Deprecated(message = "CHECK")
@CacheableTask
abstract class SignPluginTask @Inject constructor(
    objectFactory: ObjectFactory,
    execOperations: ExecOperations,
) : ZipSigningToolBase(objectFactory, execOperations) {

    /**
     * Output, signed ZIP archive file.
     * Refers to `out` CLI option.
     *
     * Predefined with the name of the ZIP archive file with `-signed` name suffix attached.
     */
    @get:OutputFile
    abstract val outputArchiveFile: RegularFileProperty

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
     * Encoded private key in the PEM format.
     * Refers to `key` CLI option.
     */
    @get:Input
    @get:Optional
    abstract val privateKey: Property<String>

    /**
     * A file with an encoded private key in the PEM format.
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

    init {
        group = PLUGIN_GROUP_NAME
        description = "Signs the ZIP archive with the provided key using marketplace-zip-signer library."
    }

    @TaskAction
    fun signPlugin() {
        executeZipSigningTool("sign")
    }


    /**
     * Collects all the options for the Plugin Verifier CLI provided with the task configuration.
     *
     * @return array with all available CLI options
     */
    override fun collectArguments(): List<String> {
        val arguments = mutableListOf(
            "-out", outputArchiveFile.asPath.pathString,
        )

        privateKey.orNull?.let {
            arguments.add("-key")

            Base64.getDecoder()
                .runCatching { decode(it.trim()).let(::String) }
                .getOrDefault(it)
                .let(arguments::add)

            debug(context, "Using private key passed as content")
        }
            ?: privateKeyFile.orNull?.let {
                arguments.add("-key-file")
                arguments.add(it.asPath.toAbsolutePath().toString())
                debug(context, "Using private key passed as file")
            }
            ?: throw InvalidUserDataException("Private key not found. One of the 'privateKey' or 'privateKeyFile' properties has to be provided.")

        certificateChain.orNull?.let {
            arguments.add("-cert")

            Base64.getDecoder()
                .runCatching { decode(it.trim()).let(::String) }
                .getOrDefault(it)
                .let(arguments::add)

            debug(context, "Using certificate chain passed as content")
        }
            ?: certificateChainFile.orNull?.let {
                arguments.add("-cert-file")
                arguments.add(it.asPath.toAbsolutePath().toString())
                debug(context, "Using certificate chain passed as file")
            }
            ?: throw InvalidUserDataException("Certificate chain not found. One of the 'certificateChain' or 'certificateChainFile' properties has to be provided.")

        password.orNull?.let {
            arguments.add("-key-pass")
            arguments.add(it)
            debug(context, "Using private key password")
        }
        keyStore.orNull?.let {
            arguments.add("-ks")
            arguments.add(it)
        }
        keyStorePassword.orNull?.let {
            arguments.add("-ks-pass")
            arguments.add(it)
        }
        keyStoreKeyAlias.orNull?.let {
            arguments.add("-ks-key-alias")
            arguments.add(it)
        }
        keyStoreType.orNull?.let {
            arguments.add("-ks-type")
            arguments.add(it)
        }
        keyStoreProviderName.orNull?.let {
            arguments.add("-ks-provider-name")
            arguments.add(it)
        }

        return arguments + super.collectArguments()
    }
}
