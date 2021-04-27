package org.jetbrains.intellij.tasks


import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.zip.signer.signer.CertificateUtils
import org.jetbrains.zip.signer.signer.PrivateKeyUtils
import org.jetbrains.zip.signer.signer.PublicKeyUtils
import org.jetbrains.zip.signer.signing.DefaultSignatureProvider
import org.jetbrains.zip.signer.signing.ZipSigner

open class SignPluginTask : ConventionTask() {
    @InputFile
    val inputArchiveFile: RegularFileProperty = project.objects.fileProperty()

    @OutputFile
    val outputArchiveFile: RegularFileProperty = project.objects.fileProperty()

    @Input
    val privateKey: Property<String> = project.objects.property(String::class.java)

    @Input
    var certificateChain: Property<String> = project.objects.property(String::class.java)

    @Input
    @Optional
    var password: String? = null

    @TaskAction
    fun signPlugin() {
        val certificateChain = CertificateUtils.loadCertificates(this.certificateChain.get())
        val privateKey = PrivateKeyUtils.loadPrivateKey(this.privateKey.get(), password?.toCharArray())

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
