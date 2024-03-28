// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PublishPluginTaskTest : IntelliJPluginTestBase() {

    @BeforeTest
    override fun setup() {
        super.setup()

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>PluginName</name>
                    <version>0.0.1</version>
                    <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                    <vendor>JetBrains</vendor>
                    <depends>com.intellij.modules.platform</depends>
                </idea-plugin>
                """.trimIndent()
    }

    @Test
    fun `fail publishing if ZIP Signer dependency is missing`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    publishing {}
                }
                """.trimIndent()

        buildAndFail(Tasks.PUBLISH_PLUGIN) {
            assertContains(
                """
                > Failed to query the value of task ':signPlugin' property 'zipSignerExecutable'.
                   > Cannot resolve the Marketplace ZIP Signer.
                     Please make sure it is added to the project with `zipSigner()` dependency helper or `intellijPlatform.signing.cliPath` extension property.
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `fail publishing if token is missing`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        zipSigner()
                    }
                }
                
                intellijPlatform {
                    publishing {}
                }
                """.trimIndent()

        buildAndFail(Tasks.PUBLISH_PLUGIN) {
            assertContains("'token' property must be specified for plugin publishing", output)
        }
    }

    @Test
    fun `fail publishing when token is not valid`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        zipSigner()
                    }
                }
                
                intellijPlatform {
                    publishing {
                        token = "foo"
                    }
                }
                """.trimIndent()

        buildAndFail(Tasks.PUBLISH_PLUGIN) {
            assertContains("Failed to upload plugin: Upload failed: Authentication Failed: Invalid token: Token is malformed", output)
        }
    }

    @Test
    fun `use signed artifact for publication`() {
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
                    publishing {
                        token = "foo"
                    }
                }
                """.trimIndent()

        buildAndFail(Tasks.PUBLISH_PLUGIN) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.BUILD_PLUGIN}")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.SIGN_PLUGIN}")?.outcome)
        }
    }

    @Test
    fun `use unsigned artifact for publication if no signing is configured`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        zipSigner()
                    }
                }
                
                intellijPlatform {
                    publishing {
                        token = "foo"
                    }
                }
                """.trimIndent()

        buildAndFail(Tasks.PUBLISH_PLUGIN) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.BUILD_PLUGIN}")?.outcome)
            assertEquals(TaskOutcome.SKIPPED, task(":${Tasks.SIGN_PLUGIN}")?.outcome)
        }
    }
}
