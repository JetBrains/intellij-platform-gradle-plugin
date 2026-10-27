// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.jetbrains.intellij.platform.gradle.*
import kotlin.test.Test

class IntelliJPlatformRepositoriesExtensionTest : IntelliJPluginTestBase() {

    @Test
    fun `custom plugin repositories do not configure authentication on the shim repository`() {
        buildFile overwrite //language=kotlin
                """
                import org.gradle.api.credentials.HttpHeaderCredentials
                import org.gradle.authentication.http.HttpHeaderAuthentication
                import org.jetbrains.intellij.platform.gradle.CustomPluginRepositoryType

                plugins {
                    id("org.jetbrains.intellij.platform")
                }

                repositories {
                    intellijPlatform {
                        customPluginRepository("https://example.invalid/simple", CustomPluginRepositoryType.SIMPLE)
                        customPluginRepository("https://example.invalid/plugins", CustomPluginRepositoryType.PLUGIN_REPOSITORY)
                        customPluginRepository("https://example.invalid/password") {
                            credentials {
                                username = "user"
                                password = "password"
                            }
                        }
                        customPluginRepository("https://example.invalid/header") {
                            credentials(HttpHeaderCredentials::class) {
                                name = "X-Repository-Token"
                                value = "token"
                            }
                            authentication {
                                create<HttpHeaderAuthentication>("header")
                            }
                        }
                    }
                }

                tasks.register("verifyCustomPluginRepositoryAuthentication") {
                    doLast {
                        val customRepositories = repositories
                            .withType(org.gradle.api.artifacts.repositories.IvyArtifactRepository::class.java)
                            .filter { it.name.startsWith("IntelliJ Platform Custom Plugin Repository") }
                        check(customRepositories.size == 4)
                        customRepositories.forEach { repository ->
                            val authentication = repository as org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal
                            check(!authentication.configuredCredentials.isPresent) {
                                "Credentials were configured on the shim repository: ${'$'}{repository.name}"
                            }
                            check(authentication.configuredAuthentication.isEmpty()) {
                                "Authentication schemes were configured on the shim repository: ${'$'}{repository.name}"
                            }
                        }
                    }
                }
                """.trimIndent()

        build("verifyCustomPluginRepositoryAuthentication") {
            assertNotContains("MissingValueException", output)
        }
    }
}
