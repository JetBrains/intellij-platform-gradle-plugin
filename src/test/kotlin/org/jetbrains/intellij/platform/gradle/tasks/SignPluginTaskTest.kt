// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SignPluginTaskTest : IntelliJPluginTestBase() {

    private val tripleQuote = "\"\"\""

    @Test
    fun `run Marketplace ZIP Signer in the latest version`() {
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

        build(Tasks.SIGN_PLUGIN, args = listOf("--debug")) {
            val message = "'Marketplace ZIP Signer specified with dependencies' resolved as: "
            val line = output.lines().find { it.contains(message) }
            assertNotNull(line)

            val path = Path(line.substringAfter(message))
            assertExists(path)
            assertTrue(path.name.startsWith("marketplace-zip-signer-"))
            assertEquals("jar", path.extension)
        }
    }

    @Test
    fun `run Marketplace ZIP Signer in specified version using certificateChainFile and privateKeyFile`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        zipSigner("0.1.21")
                    }            
                }
                
                intellijPlatform {
                    signing {
                        certificateChainFile = file("${resource("certificates/cert.crt")}")
                        privateKeyFile = file("${resource("certificates/cert.key")}")
                    }
                }
                """.trimIndent()

        build(Tasks.SIGN_PLUGIN, args = listOf("--debug")) {
            assertContains("marketplace-zip-signer-0.1.21-cli.jar", output)
        }
    }

    @Test
    fun `fail on signing without password provided`() {
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
                    }
                }
                """.trimIndent()

        buildAndFail(Tasks.SIGN_PLUGIN) {
            assertContains("Can't read private key. Password is missing", output)
        }
    }

    @Test
    fun `fail on signing with incorrect password provided`() {
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
                        password = "incorrect"
                    }
                }
                """.trimIndent()

        buildAndFail(Tasks.SIGN_PLUGIN) {
            assertContains("unable to read encrypted data", output)
        }
    }

    @Test
    fun `sign plugin with password provided`() {
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

        build(Tasks.SIGN_PLUGIN)
    }

    @Test
    fun `run Marketplace ZIP Signer in specified version using certificateChain and privateKey`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        zipSigner("0.1.21")
                    }            
                }
                
                intellijPlatform {
                    signing {
                        certificateChain = $tripleQuote${resourceContent("certificates/cert.crt")}$tripleQuote
                        privateKey = $tripleQuote${resourceContent("certificates/cert.key")}$tripleQuote
                        password = "incorrect"
                    }
                }
                """.trimIndent()

        build(Tasks.SIGN_PLUGIN, "--info") {
            assertContains("marketplace-zip-signer-0.1.21-cli.jar", output)
        }
    }

    @Test
    fun `skip Marketplace ZIP Signer task if no key and certificateChain were provided`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        zipSigner()
                    }            
                }
                """.trimIndent()

        build(Tasks.SIGN_PLUGIN) {
            assertTaskOutcome(Tasks.SIGN_PLUGIN, TaskOutcome.SKIPPED)
        }
    }

    @Test
    fun `run Marketplace ZIP Signer and fail on invalid version`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        zipSigner("0.0.1")
                    }            
                }
                
                intellijPlatform {
                    signing {
                        privateKey = "foo"
                        certificateChain = "bar"
                    }
                }
                """.trimIndent()

        build(
            gradleVersion = gradleVersion,
            fail = true,
            assertValidConfigurationCache = false,
            Tasks.SIGN_PLUGIN,
        ) {
            assertContains("No Marketplace ZIP Signer executable found.", output)
        }
    }

    @Test
    fun `output file contains version when specified in build file`() {
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

        build(Tasks.SIGN_PLUGIN)

        val distributionFolder = buildDirectory.resolve("distributions")
        assertTrue(distributionFolder.listDirectoryEntries().any {
            it.name == "projectName-1.0.0-signed.zip"
        })
    }
}
