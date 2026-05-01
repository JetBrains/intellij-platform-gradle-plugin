// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.BeforeTest
import kotlin.test.Test

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

        buildAndFail(Tasks.PUBLISH_PLUGIN, environment = pluginTemplateEnvironment()) {
            assertContains("'token' property must be specified for plugin publishing", output)
        }
    }

    @Test
    fun `use publishing token from PUBLISH_TOKEN environment variable by default`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    publishing {}
                }
                
                tasks.register("printPublishingToken") {
                    val publishingToken = intellijPlatform.publishing.token
                    inputs.property("publishingToken", publishingToken)
                
                    doLast {
                        println("publishingToken=${'$'}{publishingToken.orNull}")
                    }
                }
                """.trimIndent()

        build(
            "printPublishingToken",
            environment = pluginTemplateEnvironment(Constants.EnvironmentVariables.PUBLISH_TOKEN to "env-token"),
        ) {
            assertContains("publishingToken=env-token", output)
        }
    }

    @Test
    fun `prefer explicit publishing token over PUBLISH_TOKEN environment variable`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    publishing {
                        token = "dsl-token"
                    }
                }
                
                tasks.register("printPublishingToken") {
                    val publishingToken = intellijPlatform.publishing.token
                    inputs.property("publishingToken", publishingToken)
                
                    doLast {
                        println("publishingToken=${'$'}{publishingToken.orNull}")
                    }
                }
                """.trimIndent()

        build(
            "printPublishingToken",
            environment = pluginTemplateEnvironment(Constants.EnvironmentVariables.PUBLISH_TOKEN to "env-token"),
        ) {
            assertContains("publishingToken=dsl-token", output)
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

        buildAndFail(Tasks.PUBLISH_PLUGIN, environment = pluginTemplateEnvironment()) {
            assertContains("Failed to upload plugin: Upload failed: Authentication Failed: token is invalid", output)
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

        buildAndFail(Tasks.PUBLISH_PLUGIN, environment = pluginTemplateEnvironment()) {
            assertTaskOutcome(Tasks.BUILD_PLUGIN, TaskOutcome.SUCCESS)
            assertTaskOutcome(Tasks.SIGN_PLUGIN, TaskOutcome.SUCCESS)
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

        buildAndFail(Tasks.PUBLISH_PLUGIN, environment = pluginTemplateEnvironment()) {
            assertTaskOutcome(Tasks.BUILD_PLUGIN, TaskOutcome.SUCCESS)
            assertTaskOutcome(Tasks.SIGN_PLUGIN, TaskOutcome.SKIPPED)
        }
    }

    @Test
    fun `reuses configuration cache`() {
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

        buildAndFailWithConfigurationCache(Tasks.PUBLISH_PLUGIN, environment = pluginTemplateEnvironment()) {
            assertContains("'token' property must be specified for plugin publishing", output)
        }

        buildAndFailWithConfigurationCache(Tasks.PUBLISH_PLUGIN, environment = pluginTemplateEnvironment()) {
            assertConfigurationCacheReused()
            assertContains("'token' property must be specified for plugin publishing", output)
        }
    }
}
