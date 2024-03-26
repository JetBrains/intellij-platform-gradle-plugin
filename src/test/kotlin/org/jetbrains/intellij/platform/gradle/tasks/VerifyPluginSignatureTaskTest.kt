// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test
import kotlin.test.assertEquals

class VerifyPluginSignatureTaskTest : IntelliJPluginTestBase() {

    private val tripleQuote = "\"\"\""

    @Test
    fun `skip plugin signature verification task if plugin signing is not configured`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        zipSigner()
                    }            
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_SIGNATURE) {
            assertTaskOutcome(Tasks.VERIFY_PLUGIN_SIGNATURE, TaskOutcome.NO_SOURCE)
        }
    }

    @Test
    fun `verify plugin signature with certificateChainFile`() {
        buildFile write //language=kotlin
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

        build(Tasks.VERIFY_PLUGIN_SIGNATURE)
    }

    @Test
    fun `verify plugin signature with certificateChain`() {
        buildFile write //language=kotlin
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

        build(Tasks.VERIFY_PLUGIN_SIGNATURE)
    }

    @Test
    fun `verify plugin signed with password provided`() {
        buildFile write //language=kotlin
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

        build(Tasks.VERIFY_PLUGIN_SIGNATURE)
    }

    @Test
    fun `verify unsigned plugin archive`() {
        buildFile write //language=kotlin
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

        buildAndFail(Tasks.VERIFY_PLUGIN_SIGNATURE) {
            assertContains("Provided zip archive is not signed", output)
        }
    }
}
