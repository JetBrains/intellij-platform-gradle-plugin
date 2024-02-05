// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.test.Test
import kotlin.test.assertEquals

class VerifyPluginSignatureTaskSpec : IntelliJPluginSpecBase() {

    private val tripleQuote = "\"\"\""

    @Test
    fun `skip plugin signature verification task if plugin signing is not configured`() {
        buildFile.kotlin(
            """
            dependencies {
                intellijPlatform {
                    zipSigner()
                }            
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_SIGNATURE) {
            assertEquals(TaskOutcome.SKIPPED, task(":${Tasks.SIGN_PLUGIN}")?.outcome)
            assertEquals(TaskOutcome.NO_SOURCE, task(":${Tasks.VERIFY_PLUGIN_SIGNATURE}")?.outcome)
        }
    }

    @Test
    fun `verify plugin signature with certificateChainFile`() {
        buildFile.kotlin(
            """
            dependencies {
                intellijPlatform {
                    zipSigner()
                }            
            }
            
            intellijPlatform {
                signing {
                    certificateChainFile = file("${resource("certificates/cert.crt")}")
                    privateKeyFile = file("${resource("certificates/cert.key")}")
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_SIGNATURE)
    }

    @Test
    fun `verify plugin signature with certificateChain`() {
        buildFile.kotlin(
            """
            dependencies {
                intellijPlatform {
                    zipSigner()
                }            
            }
            
            intellijPlatform {
                signing {
                    certificateChain = $tripleQuote${resourceContent("certificates/cert.crt")}$tripleQuote
                    privateKey = $tripleQuote${resourceContent("certificates/cert.key")}$tripleQuote
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_SIGNATURE)
    }

    @Test
    fun `verify plugin signed with password provided`() {
        buildFile.kotlin(
            """
            dependencies {
                intellijPlatform {
                    zipSigner()
                }            
            }
            
            intellijPlatform {
                signing {
                    certificateChainFile = file("${resource("certificates-password/chain.crt")}")
                    privateKeyFile = file("${resource("certificates-password/private_encrypted.pem")}")
                    password = "foobar"
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_SIGNATURE)
    }

    @Test
    fun `verify unsigned plugin archive`() {
        buildFile.kotlin(
            """
            dependencies {
                intellijPlatform {
                    zipSigner("0.1.21")
                }            
            }
            
            intellijPlatform {
                signing {
                    certificateChainFile = file("${resource("certificates-password/chain.crt")}")
                    privateKeyFile = file("${resource("certificates-password/private_encrypted.pem")}")
                    password = "foobar"
                }
            }
            
            tasks {
                verifyPluginSignature {
                    inputArchiveFile.set(buildPlugin.flatMap { it.archiveFile })
                }
            }
            """.trimIndent()
        )

        buildAndFail(Tasks.VERIFY_PLUGIN_SIGNATURE) {
            assertContains("Provided zip archive is not signed", output)
        }
    }
}
