// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants.DOWNLOAD_ZIP_SIGNER_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.VERIFY_PLUGIN_SIGNATURE_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

@Suppress("GroovyUnusedAssignment", "ComplexRedundantLet")
class SignPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `run Marketplace ZIP Signer in the latest version`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            signPlugin {
                certificateChainFile = file("${resolveResourcePath("certificates/cert.crt")}")
                privateKeyFile = file("${resolveResourcePath("certificates/cert.key")}")
            }
            
            downloadZipSigner {
                doLast {
                    println cli.get()     
                    println "version:" + version.get()
                }
            }
            """.trimIndent()
        )

        val version = DownloadZipSignerTask.resolveLatestVersion()
        build(DOWNLOAD_ZIP_SIGNER_TASK_NAME, "--info").let {
            assertContains("marketplace-zip-signer-cli.jar", it.output)
            assertContains("version:latest", it.output)
        }
    }

    @Test
    fun `run Marketplace ZIP Signer in specified version using certificateChainFile and privateKeyFile`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            downloadZipSigner {
                version = "0.1.7"
            }
            signPlugin {
                certificateChainFile = file("${resolveResourcePath("certificates/cert.crt")}")
                privateKeyFile = file("${resolveResourcePath("certificates/cert.key")}")
            }
            """.trimIndent()
        )

        build(SIGN_PLUGIN_TASK_NAME, "--info").let {
            assertContains("marketplace-zip-signer-cli.jar", it.output)
        }
    }

    @Test
    fun `fail on signing without password provided`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            downloadZipSigner {
                version = "0.1.7"
            }
            signPlugin {
                certificateChainFile = file("${resolveResourcePath("certificates-password/chain.crt")}")
                privateKeyFile = file("${resolveResourcePath("certificates-password/private_encrypted.pem")}")
            }
            """.trimIndent()
        )

        buildAndFail(SIGN_PLUGIN_TASK_NAME).let {
            assertContains("Can't read private key. Password is missing", it.output)
        }
    }

    @Test
    fun `fail on signing with incorrect password provided`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            downloadZipSigner {
                version = "0.1.7"
            }
            signPlugin {
                certificateChainFile = file("${resolveResourcePath("certificates-password/chain.crt")}")
                privateKeyFile = file("${resolveResourcePath("certificates-password/private_encrypted.pem")}")
                password = "incorrect"
            }
            """.trimIndent()
        )

        buildAndFail(SIGN_PLUGIN_TASK_NAME).let {
            assertContains("unable to read encrypted data", it.output)
        }
    }

    @Test
    fun `sign plugin with password provided`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            downloadZipSigner {
                version = "0.1.7"
            }
            signPlugin {
                certificateChainFile = file("${resolveResourcePath("certificates-password/chain.crt")}")
                privateKeyFile = file("${resolveResourcePath("certificates-password/private_encrypted.pem")}")
                password = "foobar"
            }
            """.trimIndent()
        )

        build(SIGN_PLUGIN_TASK_NAME)
    }

    @Test
    fun `run Marketplace ZIP Signer in specified version using certificateChain and privateKey`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            downloadZipSigner {
                version = "0.1.7"
            }
            signPlugin {
                certificateChain = ${"\"\"\""}${resolveResourceContent("certificates/cert.crt")}${"\"\"\""}
                privateKey = ${"\"\"\""}${resolveResourceContent("certificates/cert.key")}${"\"\"\""}
            }
            """.trimIndent()
        )

        build(SIGN_PLUGIN_TASK_NAME, "--info").let {
            assertContains("marketplace-zip-signer-cli.jar", it.output)
        }
    }

    @Test
    fun `skip Marketplace ZIP Signer task if no key and certificateChain were provided`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            """.trimIndent()
        )

        build(SIGN_PLUGIN_TASK_NAME).let {
            assertContains("Task :$SIGN_PLUGIN_TASK_NAME SKIPPED", it.output)
        }
    }

    @Test
    fun `run Marketplace ZIP Signer and fail on invalid version`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            downloadZipSigner {
                version = "0.0.1"
            }
            """.trimIndent()
        )

        build(
            gradleVersion = gradleVersion,
            fail = true,
            assertValidConfigurationCache = false,
            SIGN_PLUGIN_TASK_NAME,
        ).let {
            assertContains("Could not find org.jetbrains:marketplace-zip-signer-cli:0.0.1.", it.output)
        }
    }

    @Test
    fun `output file contains version when specified in build file`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            signPlugin {
                certificateChainFile = file("${resolveResourcePath("certificates/cert.crt")}")
                privateKeyFile = file("${resolveResourcePath("certificates/cert.key")}")
            }
            """.trimIndent()
        )
        build(SIGN_PLUGIN_TASK_NAME)

        val distributionFolder = File(buildDirectory, "distributions")
        assertTrue(distributionFolder.listFiles()?.asList()?.any {
            it.name.equals("projectName-1.0.0-signed.zip")
        } ?: false)
    }

    @Test
    fun `skip plugin signature verification task if plugin signing is not configured`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            """.trimIndent()
        )

        build(VERIFY_PLUGIN_SIGNATURE_TASK_NAME).let {
            assertContains("Task :$SIGN_PLUGIN_TASK_NAME SKIPPED", it.output)
            assertContains("Task :$VERIFY_PLUGIN_SIGNATURE_TASK_NAME NO-SOURCE", it.output)
        }
    }

    @Test
    fun `verify plugin signature with certificateChainFile`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            signPlugin {
                certificateChainFile = file("${resolveResourcePath("certificates/cert.crt")}")
                privateKeyFile = file("${resolveResourcePath("certificates/cert.key")}")
            }
            """.trimIndent()
        )
        build(VERIFY_PLUGIN_SIGNATURE_TASK_NAME)
    }

    @Test
    fun `verify plugin signature with certificateChain`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            downloadZipSigner {
                version = "0.1.7"
            }
            signPlugin {
                certificateChain = ${"\"\"\""}${resolveResourceContent("certificates/cert.crt")}${"\"\"\""}
                privateKey = ${"\"\"\""}${resolveResourceContent("certificates/cert.key")}${"\"\"\""}
            }
            """.trimIndent()
        )

        build(VERIFY_PLUGIN_SIGNATURE_TASK_NAME)
    }

    @Test
    fun `verify plugin signed with password provided`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            downloadZipSigner {
                version = "0.1.7"
            }
            signPlugin {
                certificateChainFile = file("${resolveResourcePath("certificates-password/chain.crt")}")
                privateKeyFile = file("${resolveResourcePath("certificates-password/private_encrypted.pem")}")
                password = "foobar"
            }
            """.trimIndent()
        )

        build(VERIFY_PLUGIN_SIGNATURE_TASK_NAME)
    }

    @Test
    fun `verify unsigned plugin archive`() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            downloadZipSigner {
                version = "0.1.7"
            }
            signPlugin {
                certificateChainFile = file("${resolveResourcePath("certificates-password/chain.crt")}")
                privateKeyFile = file("${resolveResourcePath("certificates-password/private_encrypted.pem")}")
                password = "foobar"
            }
            verifyPluginSignature {
                inputArchiveFile = buildPlugin.archiveFile
            }
            """.trimIndent()
        )

        buildAndFail(VERIFY_PLUGIN_SIGNATURE_TASK_NAME).let {
            assertContains("Provided zip archive is not signed", it.output)
        }
    }
}
