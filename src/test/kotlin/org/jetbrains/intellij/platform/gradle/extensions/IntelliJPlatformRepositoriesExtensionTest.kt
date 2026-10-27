// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertNotContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.overwrite
import org.jetbrains.intellij.platform.gradle.settingsFile
import kotlin.test.Test

class IntelliJPlatformRepositoriesExtensionTest : IntelliJPluginTestBase() {

    @Test
    fun `custom plugin repositories do not configure authentication on the shim repository`() {
        buildFile overwrite //language=kotlin
            """
                import org.gradle.api.credentials.HttpHeaderCredentials
                import org.gradle.api.credentials.PasswordCredentials
                import org.gradle.authentication.http.HttpHeaderAuthentication
                import org.jetbrains.intellij.platform.gradle.CustomPluginRepositoryType
                import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform
                import org.jetbrains.intellij.platform.gradle.artifacts.repositories.PluginArtifactRepository

                plugins {
                    id("org.jetbrains.intellij.platform")
                }

                val customPluginRepositories = mutableListOf<PluginArtifactRepository>()

                repositories {
                    intellijPlatform {
                        customPluginRepositories += customPluginRepository("https://example.invalid/simple", CustomPluginRepositoryType.SIMPLE)
                        customPluginRepositories += customPluginRepository("https://example.invalid/plugins", CustomPluginRepositoryType.PLUGIN_REPOSITORY)
                        customPluginRepositories += customPluginRepository("https://example.invalid/password") {
                            credentials {
                                username = "user"
                                password = "password"
                            }
                        }
                        customPluginRepositories += customPluginRepository("https://example.invalid/header") {
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
                        check(customPluginRepositories.size == 4)
                        val shimRepositories = repositories
                            .withType(org.gradle.api.artifacts.repositories.IvyArtifactRepository::class.java)
                            .filter { it.name.startsWith("IntelliJ Platform Custom Plugin Repository") }
                        check(shimRepositories.size == 4)
                        shimRepositories.forEach { repository ->
                            val authentication = repository as org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal
                            check(!authentication.configuredCredentials.isPresent)
                            check(authentication.configuredAuthentication.isEmpty())
                        }
                        check(customPluginRepositories[2].getCredentials(PasswordCredentials::class.java).username == "user")
                        check(customPluginRepositories[2].getCredentials(PasswordCredentials::class.java).password == "password")
                        check(customPluginRepositories[3].getCredentials(HttpHeaderCredentials::class.java).name == "X-Repository-Token")
                        check(customPluginRepositories[3].getCredentials(HttpHeaderCredentials::class.java).value == "token")
                    }
                }
                """.trimIndent()

        build("verifyCustomPluginRepositoryAuthentication") {
            assertNotContains("MissingValueException", output)
        }
    }

    @Test
    fun `custom plugin repositories in settings do not configure authentication on the shim repository`() {
        settingsFile overwrite //language=kotlin
            """
                import org.jetbrains.intellij.platform.gradle.CustomPluginRepositoryType
                import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

                plugins {
                    id("org.jetbrains.intellij.platform.settings")
                }

                dependencyResolutionManagement {
                    repositories {
                        intellijPlatform {
                            customPluginRepository("https://example.invalid/settings", CustomPluginRepositoryType.SIMPLE)
                        }
                    }
                }

                val repository = dependencyResolutionManagement.repositories
                    .withType(org.gradle.api.artifacts.repositories.IvyArtifactRepository::class.java)
                    .single { it.name == "IntelliJ Platform Custom Plugin Repository (https://example.invalid/settings)" }
                val authentication = repository as org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal
                check(!authentication.configuredCredentials.isPresent)
                check(authentication.configuredAuthentication.isEmpty())
                """.trimIndent()

        buildFile overwrite //language=kotlin
            """
                tasks.register("verifySettingsRepository")
                """.trimIndent()

        build("help") {
            assertNotContains("MissingValueException", output)
        }
    }
}
