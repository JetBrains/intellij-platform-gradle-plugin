package org.jetbrains.intellij.tasks

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.internal.ExecException
import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.error
import org.jetbrains.intellij.logCategory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class SignPluginTask @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val execOperations: ExecOperations,
) : ConventionTask() {

    companion object {
        private const val LATEST_RELEASE_URL = "https://github.com/JetBrains/marketplace-zip-signer/releases/latest"

        /**
         * Resolves the latest version available of the Marketplace ZIP Signer CLI using GitHub API.
         *
         * @return latest CLI version
         */
        fun resolveLatestVersion(): String {
            debug(message = "Resolving latest Marketplace ZIP Signer CLI version")
            try {
                return URL(LATEST_RELEASE_URL).openConnection().run {
                    (this as HttpURLConnection).instanceFollowRedirects = false
                    getHeaderField("Location").split('/').last()
                }
            } catch (e: IOException) {
                throw GradleException("Cannot resolve the latest Marketplace ZIP Signer CLI version")
            }
        }

        /**
         * Resolves Marketplace ZIP Signer CLI version.
         * If set to {@link IntelliJPluginConstants#VERSION_LATEST}, there's request to {@link #METADATA_URL}
         * performed for the latest available verifier version.
         *
         * @return Marketplace ZIP Signer CLI version
         */
        fun resolveCliVersion(version: String?) = version?.takeIf { it != IntelliJPluginConstants.VERSION_LATEST }
            ?: resolveLatestVersion()

        /**
         * Resolves Marketplace ZIP Signer CLI download URL.
         *
         * @return Marketplace ZIP Signer CLI download URL
         */
        fun resolveCliUrl(version: String?) = resolveCliVersion(version).let {
            "https://github.com/JetBrains/marketplace-zip-signer/releases/download/$it/marketplace-zip-signer-cli.jar"
        }
    }

    /**
     * Input, unsigned ZIP archive file.
     * Refers to `in` option.
     */
    @InputFile
    @SkipWhenEmpty
    val inputArchiveFile: RegularFileProperty = objectFactory.fileProperty()

    /**
     * Output, signed ZIP archive file.
     * Refers to `out` option.
     */
    @OutputFile
    val outputArchiveFile: RegularFileProperty = objectFactory.fileProperty()

    /**
     * Returns the version of the Marketplace ZIP Signer CLI that will be used.
     * By default, set to "latest".
     */
    @Input
    @Optional
    val cliVersion: Property<String> = objectFactory.property(String::class.java)

    /**
     * Local path to the Marketplace ZIP Signer CLI that will be used.
     * If provided, {@link #cliVersion} is ignored.
     */
    @Input
    @Optional
    val cliPath: Property<String> = objectFactory.property(String::class.java)

    /**
     * KeyStore file path.
     * Refers to `ks` option.
     */
    @Input
    @Optional
    val keyStore: Property<String> = objectFactory.property(String::class.java)

    /**
     * KeyStore password.
     * Refers to `ks-pass` option.
     */
    @Input
    @Optional
    val keyStorePassword: Property<String> = objectFactory.property(String::class.java)

    /**
     * KeyStore key alias.
     * Refers to `ks-key-alias` option.
     */
    @Input
    @Optional
    val keyStoreKeyAlias: Property<String> = objectFactory.property(String::class.java)

    /**
     * KeyStore type.
     * Refers to `ks-type` option.
     */
    @Input
    @Optional
    val keyStoreType: Property<String> = objectFactory.property(String::class.java)

    /**
     * JCA KeyStore Provider name.
     * Refers to `ks-provider-name` option.
     */
    @Input
    @Optional
    val keyStoreProviderName: Property<String> = objectFactory.property(String::class.java)

    /**
     * Private key content.
     * Refers to `key` option.
     */
    @Input
    @Optional
    val privateKey: Property<String> = objectFactory.property(String::class.java)

    /**
     * Private key file.
     * Refers to `key-file` option.
     */
    @InputFile
    @Optional
    val privateKeyFile: RegularFileProperty = objectFactory.fileProperty()

    /**
     * Private key password.
     * Refers to `key-pass` option.
     */
    @Input
    @Optional
    val password: Property<String> = objectFactory.property(String::class.java)

    /**
     * Certificate chain content.
     * First certificate from the chain will be used as a certificate authority (CA).
     * Refers to `cert` option.
     */
    @Input
    @Optional
    val certificateChain: Property<String> = objectFactory.property(String::class.java)

    /**
     * Certificate chain file.
     * First certificate from the chain will be used as a certificate authority (CA).
     * Refers to `cert-file` option.
     */
    @InputFile
    @Optional
    val certificateChainFile: RegularFileProperty = objectFactory.fileProperty()

    private val context = logCategory()

    @TaskAction
    fun signPlugin() {
        val file = inputArchiveFile.orNull
        if (file == null || !file.asFile.exists()) {
            throw IllegalStateException("Plugin file does not exist: $file")
        }

        if (privateKey.orNull == null && privateKeyFile.orNull == null) {
            throw InvalidUserDataException(
                "Private key not found. " +
                    "One of the 'signPlugin.privateKey' or 'signPlugin.privateKeyFile' properties has to be provided."
            )
        }
        if (certificateChain.orNull == null && certificateChainFile.orNull == null) {
            throw InvalidUserDataException(
                "Certificate chain not found. " +
                    "One of the 'signPlugin.certificateChain' or 'signPlugin.certificateChainFile' properties has to be provided."
            )
        }

        val cliPath = resolveCliPath()
        val cliArgs = listOf("sign") + getOptions()

        debug(context, "Distribution file: ${file.asFile.canonicalPath}")
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
     * Resolves path to the IntelliJ Marketplace ZIP Signer.
     * At first, checks if it was provided with {@link #cliPath}.
     * Fetches Marketplace ZIP Signer artifact from the GitHub Releases and resolves the path to zip-signer-cli jar file.
     *
     * @return path to zip-signer-cli jar
     */
    private fun resolveCliPath(): String {
        val path = cliPath.orNull
        if (path != null && path.isNotEmpty()) {
            val verifier = File(path)
            if (verifier.exists()) {
                return path
            }
        }

        throw InvalidUserDataException("Provided Marketplace ZIP Signer path doesn't exist: '$path'. Downloading Marketplace ZIP Signer: $cliVersion")
    }

    /**
     * Collects all the options for the Plugin Verifier CLI provided with the task configuration.
     *
     * @return array with available CLI options
     */
    private fun getOptions(): List<String> {
        val args = mutableListOf(
            "-in", inputArchiveFile.get().asFile.canonicalPath,
            "-out", outputArchiveFile.get().asFile.canonicalPath,
        )

        privateKey.orNull?.let {
            args.add("-key")
            args.add(it)
            debug(context, "Using private key passed as content")
        } ?: run {
            args.add("-key-file")
            args.add(privateKeyFile.get().asFile.canonicalPath)
            debug(context, "Using private key passed as file")
        }
        certificateChain.orNull?.let {
            args.add("-cert")
            args.add(it)
            debug(context, "Using certificate chain passed as content")
        } ?: run {
            args.add("-cert-file")
            args.add(certificateChainFile.get().asFile.canonicalPath)
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
