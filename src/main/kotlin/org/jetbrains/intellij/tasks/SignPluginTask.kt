package org.jetbrains.intellij.tasks

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.zip.signer.signer.CertificateUtils
import org.jetbrains.zip.signer.signer.PrivateKeyUtils
import org.jetbrains.zip.signer.signer.PublicKeyUtils
import org.jetbrains.zip.signer.signing.DefaultSignatureProvider
import org.jetbrains.zip.signer.signing.ZipSigner
import java.security.Security
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class SignPluginTask @Inject constructor(
    objectFactory: ObjectFactory,
) : ConventionTask() {

    @InputFile
    val inputArchiveFile: RegularFileProperty = objectFactory.fileProperty()

    @OutputFile
    val outputArchiveFile: RegularFileProperty = objectFactory.fileProperty()

    @Input
    val privateKey: Property<String> = objectFactory.property(String::class.java)

    @Input
    val certificateChain: Property<String> = objectFactory.property(String::class.java)

    @Input
    @Optional
    val password: Property<String> = objectFactory.property(String::class.java)

    @TaskAction
    @ExperimentalUnsignedTypes
    fun signPlugin() {
        Security.addProvider(BouncyCastleProvider())

        val certificateChain = CertificateUtils.loadCertificates(this.certificateChain.get())
        val privateKey = PrivateKeyUtils.loadPrivateKey(this.privateKey.get(), password.orNull?.toCharArray())

        ZipSigner.sign(
            inputArchiveFile.get().asFile,
            outputArchiveFile.get().asFile,
            certificateChain,
            DefaultSignatureProvider(
                PublicKeyUtils.getSuggestedSignatureAlgorithm(certificateChain[0].publicKey),
                privateKey
            )
        )
    }
}
