// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants.DOWNLOAD_ZIP_SIGNER_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME
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
    fun `run Marketplace ZIP Signer in specified version`() {
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
}
