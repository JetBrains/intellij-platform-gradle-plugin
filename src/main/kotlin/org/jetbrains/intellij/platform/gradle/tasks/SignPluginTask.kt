// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.aware.SigningAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import org.jetbrains.intellij.platform.gradle.utils.isSpecified
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

/**
 * Signs the ZIP archive with the provided key using [Marketplace ZIP Signer](https://github.com/JetBrains/marketplace-zip-signer) library.
 *
 * To sign the plugin before publishing to [JetBrains Marketplace](https://plugins.jetbrains.com) with the [SignPluginTask] task,
 * it is required to provide a certificate chain and a private key with its password using [IntelliJPlatformExtension.Signing] extension.
 *
 * As soon as [privateKey] (or [privateKeyFile]) and [certificateChain] (or [certificateChainFile]) properties are specified, the task will be executed automatically right before the [PublishPluginTask] task.
 *
 * For more details, see [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) article.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-signing.html">Plugin Signing</a>
 * @see <a href="https://github.com/JetBrains/marketplace-zip-signer">Marketplace ZIP Signer</a>
 */
@CacheableTask
abstract class SignPluginTask : JavaExec(), SigningAware {

    /**
     * Input, unsigned ZIP archive file.
     * Refers to `in` CLI option.
     */
    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archiveFile: RegularFileProperty

    /**
     * Output, signed ZIP archive file.
     * Refers to `out` CLI option.
     *
     * Predefined with the name of the ZIP archive file with `-signed` name suffix attached.
     */
    @get:OutputFile
    abstract val signedArchiveFile: RegularFileProperty

    /**
     * KeyStore file.
     * Refers to `ks` CLI option.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val keyStore: RegularFileProperty

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
     *
     * Takes precedence over the [privateKeyFile] property.
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
    @get:PathSensitive(PathSensitivity.RELATIVE)
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
     *
     * Takes precedence over the [certificateChainFile] property.
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
        val executable = zipSignerExecutable.orNull?.asPath
            ?: throw GradleException(
                """
                No Marketplace ZIP Signer executable found.
                Please ensure the `zipSigner()` entry is present in the project dependencies section or `intellijPlatform.signing.cliPath` extension property
                See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-signing
                """.trimIndent()
            )

        log.debug("Marketplace ZIP Signer CLI path: $executable")

        classpath = objectFactory.fileCollection().from(executable)
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
        val file = archiveFile.orNull?.let { regularFile ->
            requireNotNull(regularFile.asPath.takeIf { it.exists() }) { "Plugin file does not exist: $regularFile" }
        }
        requireNotNull(file) { "Input archive file is not provided." }

        log.debug("Distribution file: $file")

        yield("-in")
        yield(file.pathString)

        yield("-out")
        yield(signedArchiveFile.asPath.pathString)

        privateKey.orNull?.let {
            yield("-key")
            yield(Base64.getDecoder().runCatching { decode(it.trim()).let(::String) }.getOrDefault(it))

            log.debug("Using private key passed as content")
        } ?: privateKeyFile.orNull?.let {
            yield("-key-file")
            yield(it.asPath.pathString)
            log.debug("Using private key passed as file")
        } ?: throw InvalidUserDataException("Private key not found. One of the 'privateKey' or 'privateKeyFile' properties has to be provided.")

        certificateChain.orNull?.let {
            yield("-cert")
            yield(Base64.getDecoder().runCatching { decode(it.trim()).let(::String) }.getOrDefault(it))

            log.debug("Using certificate chain passed as content")
        } ?: certificateChainFile.orNull?.let {
            yield("-cert-file")
            yield(it.asPath.pathString)
            log.debug("Using certificate chain passed as file")
        }
        ?: throw InvalidUserDataException("Certificate chain not found. One of the 'certificateChain' or 'certificateChainFile' properties has to be provided.")

        password.orNull?.let {
            yield("-key-pass")
            yield(it)
            log.debug("Using private key password")
        }
        keyStore.orNull?.let {
            yield("-ks")
            yield(it.asPath.pathString)
        }
        keyStorePassword.orNull?.let {
            yield("-ks-pass")
            yield(it)
        }
        keyStoreKeyAlias.orNull?.let {
            yield("-ks-key-alias")
            yield(it)
        }
        keyStoreType.orNull?.let {
            yield("-ks-type")
            yield(it)
        }
        keyStoreProviderName.orNull?.let {
            yield("-ks-provider-name")
            yield(it)
        }
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Signs the ZIP archive with the provided key using marketplace-zip-signer library."

        mainClass.set("org.jetbrains.zip.signer.ZipSigningTool")
        args = listOf("sign")
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<SignPluginTask>(Tasks.SIGN_PLUGIN) {
                val signingProvider = project.extensionProvider.map { it.signing }
                val buildPluginTaskProvider = project.tasks.named<BuildPluginTask>(Tasks.BUILD_PLUGIN)

                archiveFile.convention(buildPluginTaskProvider.flatMap { it.archiveFile })
                signedArchiveFile.convention(project.layout.file(
                    archiveFile
                        .map { it.asPath }
                        .map { it.resolveSibling(it.nameWithoutExtension + "-signed." + it.extension).toFile() })
                )
                keyStore.convention(signingProvider.flatMap { it.keyStore })
                keyStorePassword.convention(signingProvider.flatMap { it.keyStorePassword })
                keyStoreKeyAlias.convention(signingProvider.flatMap { it.keyStoreKeyAlias })
                keyStoreType.convention(signingProvider.flatMap { it.keyStoreType })
                keyStoreProviderName.convention(signingProvider.flatMap { it.keyStoreProviderName })
                privateKey.convention(signingProvider.flatMap { it.privateKey })
                privateKeyFile.convention(signingProvider.flatMap { it.privateKeyFile })
                password.convention(signingProvider.flatMap { it.password })
                certificateChain.convention(signingProvider.flatMap { it.certificateChain })
                certificateChainFile.convention(signingProvider.flatMap { it.certificateChainFile })

                onlyIf {
                    (privateKey.isSpecified() || privateKeyFile.isSpecified()) && (certificateChain.isSpecified() || certificateChainFile.isSpecified())
                }
            }
    }
}
