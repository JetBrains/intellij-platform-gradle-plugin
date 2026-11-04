// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.jetbrains.intellij.platform.gradle.*
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.BeforeTest
import kotlin.test.Test

class IntelliJPlatformPluginVerificationIdesTest : IntelliJPlatformTestBase() {

    private val localIdePath
        get() = dir.resolve("local-ide")

    private val productReleasesProperties
        get() = mapOf(
            GradleProperties.ProductsReleasesCdnBuildsUrl.toString() to
                    requireNotNull(javaClass.classLoader.getResource("products-releases/jetbrains-product-releases-IC.json"))
                        .toString().replace("IC.json", "{type}.json"),
            GradleProperties.ProductsReleasesAndroidStudioUrl.toString() to
                    requireNotNull(javaClass.classLoader.getResource("products-releases/android-studio-releases-list.json")).toString(),
        )

    @BeforeTest
    override fun setup() {
        super.setup()

        settingsFile write "rootProject.name = \"plugin-verification-ides\""
        gradleProperties write "org.jetbrains.intellij.platform.selfUpdateCheck=false"

        localIdePath.resolve("product-info.json") write //language=json
                """
                {
                    "name": "IntelliJ IDEA Community Edition",
                    "version": "2023.3.8",
                    "buildNumber": "233.15619.7",
                    "productCode": "IC"
                }
                """.trimIndent()

        buildFile write //language=kotlin
                """
                import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

                plugins {
                    id("org.jetbrains.intellij.platform")
                }

                repositories {
                    intellijPlatform {
                        localPlatformArtifacts()
                    }
                }

                dependencies {
                    intellijPlatform {
                        local("${localIdePath.invariantSeparatorsPathString}")
                    }
                }

                intellijPlatform {
                    caching.ides.enabled = false
                    pluginConfiguration.ideaVersion {
                        sinceBuild = "233"
                        untilBuild = "233.*"
                    }
                }

                val verifierIdes = configurations["intellijPluginVerifierIdes"]
                tasks.register("printVerifierIdes") {
                    inputs.property("ides", provider {
                        verifierIdes.allDependencies.map { "${'$'}{it.group}:${'$'}{it.name}:${'$'}{it.version}" }
                    })
                    doLast {
                        println("VERIFIER_IDES=" + inputs.properties["ides"])
                    }
                }
                """.trimIndent()
    }

    @Test
    fun `explicit local ide skips automatic recommendations and base platform resolution`() {
        rejectAutomaticRecommendations()
        rejectBasePlatformResolution()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    caching.ides.enabled = true
                    pluginVerification.ides.local("${localIdePath.invariantSeparatorsPathString}")
                }
                """.trimIndent()

        build("printVerifierIdes") {
            assertContains("VERIFIER_IDES=[localIde:IC:IC-233.15619.7]", output)
        }

        repeat(2) { iteration ->
            buildWithConfigurationCache("printVerifierIdes") {
                assertContains("VERIFIER_IDES=[localIde:IC:IC-233.15619.7]", output)
                if (iteration == 1) {
                    assertConfigurationCacheReused()
                }
            }
        }
    }

    @Test
    fun `verification IDE files use the shared cache and support configuration cache reuse`() {
        val cacheDirectory = dir.resolve("ides-cache")
        val cachedIde = cacheDirectory.resolve("IC-2023.3.8")
        val listingsFile = dir.resolve("empty-listings.json")
        listingsFile write "[]"
        cachedIde.resolve("product-info.json") write //language=json
                """
                {
                    "name": "IntelliJ IDEA Community Edition",
                    "version": "2023.3.8",
                    "buildNumber": "233.15619.7",
                    "productCode": "IC"
                }
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    caching.ides {
                        enabled = true
                        path = file("${cacheDirectory.invariantSeparatorsPathString}")
                    }
                    pluginVerification.ides.create("IC", "2023.3.8")
                }

                configurations.configureEach {
                    incoming.beforeResolve {
                        error("Cached verification IDE must not resolve archives: " + name)
                    }
                }

                val verifierIdeFiles = tasks.named<org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask>("verifyPlugin").get().ides
                tasks.register("printVerifierIdeFiles") {
                    inputs.files(verifierIdeFiles)
                    doLast {
                        println("VERIFIER_IDE_FILES=" + inputs.files.files.map { it.invariantSeparatorsPath })
                    }
                }
                """.trimIndent()

        repeat(2) { iteration ->
            buildWithConfigurationCache(
                "printVerifierIdeFiles",
                projectProperties = mapOf(
                    GradleProperties.ProductsReleasesCdnBuildsUrl.toString() to listingsFile.toUri().toString(),
                ),
            ) {
                assertContains("VERIFIER_IDE_FILES=[${cachedIde.invariantSeparatorsPathString}]", output)
                if (iteration == 1) {
                    assertConfigurationCacheReused()
                }
            }
        }
    }

    @Test
    fun `current ide skips automatic recommendations`() {
        rejectAutomaticRecommendations()

        buildFile write //language=kotlin
                """
                intellijPlatform.pluginVerification.ides.current()
                """.trimIndent()

        build("printVerifierIdes") {
            assertContains("VERIFIER_IDES=[localIde:IC:IC-233.15619.7]", output)
        }
    }

    @Test
    fun `explicit remote ides skip automatic recommendations`() {
        rejectAutomaticRecommendations()

        buildFile write //language=kotlin
                """
                intellijPlatform.pluginVerification.ides {
                    create("IC", "2024.1") {
                        useInstaller = false
                    }
                    create(providers.provider { listOf("IC-2024.2") }) { notation ->
                        type = IntelliJPlatformType.IntellijIdeaCommunity
                        version = notation.removePrefix("IC-")
                        useInstaller = false
                    }
                }
                """.trimIndent()

        build("printVerifierIdes") {
            assertContains(
                "VERIFIER_IDES=[com.jetbrains.intellij.idea:ideaIC:2024.1, com.jetbrains.intellij.idea:ideaIC:2024.2]",
                output,
            )
        }
    }

    @Test
    fun `disabled automatic recommendations do not resolve the base platform`() {
        rejectAutomaticRecommendations()
        rejectBasePlatformResolution()

        build(
            "printVerifierIdes",
            projectProperties = mapOf(GradleProperties.VerifyPluginDefaultRecommendedIdes.toString() to false),
        ) {
            assertContains("VERIFIER_IDES=[]", output)
        }
    }

    @Test
    fun `automatic recommendations remain enabled by default`() {
        build("printVerifierIdes", projectProperties = productReleasesProperties) {
            assertContains("idea:ideaIC:2023.3.8", output)
        }
    }

    @Test
    fun `explicit recommendations remain enabled when automatic recommendations are disabled`() {
        buildFile write //language=kotlin
                """
                intellijPlatform.pluginVerification.ides.recommended()
                """.trimIndent()

        build(
            "printVerifierIdes",
            projectProperties = productReleasesProperties + mapOf(
                GradleProperties.VerifyPluginDefaultRecommendedIdes.toString() to false,
            ),
        ) {
            assertContains("idea:ideaIC:2023.3.8", output)
        }
    }

    private fun rejectAutomaticRecommendations() {
        buildFile write //language=kotlin
                """
                intellijPlatform.pluginConfiguration.ideaVersion.sinceBuild = providers.provider {
                    error("UNUSED_RECOMMENDATIONS_EVALUATED")
                }
                """.trimIndent()
    }

    private fun rejectBasePlatformResolution() {
        buildFile write //language=kotlin
                """
                configurations["intellijPlatformDependency"].incoming.beforeResolve {
                    error("UNUSED_BASE_PLATFORM_RESOLVED")
                }
                """.trimIndent()
    }
}