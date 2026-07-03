// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Ignore
import kotlin.test.Test

class IntelliJPlatformDependenciesExtensionTest : IntelliJPluginTestBase() {

    @Test
    @Ignore("When using cache, this warning is not emitted.")
    fun `warn when using Rider with useInstaller true`() {
        gradleProperties write //language=properties
                """
                intellijPlatform.type=RD
                """.trimIndent()

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            assertContains("Using Rider as a target IntelliJ Platform with `useInstaller = true` is currently not supported, please set `useInstaller = false` instead.", output)
        }
    }

    @Test
    fun `do not warn when using Rider with useInstaller false`() {
        gradleProperties write //language=properties
                """
                intellijPlatform.type=RD
                intellijPlatform.useInstaller=false
                """.trimIndent()

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            assertNotContains("Using Rider as a target IntelliJ Platform with `useInstaller = true` is currently not supported, please set `useInstaller = false` instead.", output)
        }
    }

    @Test
    fun `create frontend dependency resolves JetBrains Client installer`() {
        val buildNumber = "251.28774.11"
        val artifact = currentJetBrainsClientArtifact(buildNumber)
        val repository = dir.resolve("jetbrains-client-repository")
        val artifactPath = repository.resolve("idea/code-with-me/${artifact.fileName}")
        writeJetBrainsClientArchive(artifactPath, artifact.extension, buildNumber)

        buildFile overwrite //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform")
                }
                
                repositories {
                    ivy {
                        url = uri("${repository.invariantSeparatorsPathString}")
                        patternLayout {
                            artifact("[organization]/[module]-[revision](-[classifier]).[ext]")
                            artifact("[organization]/[module]-[revision](.[classifier]).[ext]")
                        }
                        metadataSources {
                            artifact()
                        }
                        content {
                            includeModule("idea/code-with-me", "JetBrainsClient")
                        }
                    }
                    
                    intellijPlatform {
                        localPlatformArtifacts()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        create("IU", "2025.1.6") {
                            productMode.set(org.jetbrains.intellij.platform.gradle.ProductMode.FRONTEND)
                        }
                    }
                }
                """.trimIndent()

        build(
            "dependencies",
            "--configuration",
            "intellijPlatformLocal",
            projectProperties = mapOf(
                GradleProperties.ProductsReleasesCdnBuildsUrl.toString() to resourceUrl("products-releases/jetbrains-product-releases-IC.json").toString().replace("IC.json", "{type}.json"),
            ),
        ) {
            assertContains("localIde:JBC:JBC-$buildNumber", output)
        }
    }

    @Test
    fun `testFramework excludes bundled IntelliJ Platform modules from transitive dependencies`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        testFramework(TestFrameworkType.Platform)
                    }
                }
                """.trimIndent()

        build("dependencies", "--configuration=intellijPlatformTestDependencies") {
            assertContains("com.jetbrains.intellij.platform:test-framework", output)
            assertNotContains("com.jetbrains.intellij.platform:boot", output)
            assertNotContains("com.jetbrains.intellij.platform:util", output)
            assertNotContains("com.jetbrains.intellij.platform:lang-impl", output)
            assertNotContains("com.jetbrains.intellij.platform:ide-impl", output)
            assertNotContains("com.jetbrains.intellij.platform:core-ui", output)
        }
    }

    private fun currentJetBrainsClientArtifact(buildNumber: String): JetBrainsClientArtifact {
        val arch = System.getProperty("os.arch").takeIf { it == "aarch64" }

        return with(OperatingSystem.current()) {
            when {
                isLinux -> JetBrainsClientArtifact(
                    fileName = "JetBrainsClient-$buildNumber${arch?.let { "-$it" }.orEmpty()}.tar.gz",
                    extension = "tar.gz",
                )

                isWindows -> {
                    val classifier = when (arch) {
                        null -> "jbr.win"
                        else -> "$arch.jbr.win"
                    }
                    JetBrainsClientArtifact(
                        fileName = "JetBrainsClient-$buildNumber.$classifier.zip",
                        extension = "zip",
                    )
                }

                isMacOsX -> JetBrainsClientArtifact(
                    fileName = "JetBrainsClient-$buildNumber${arch?.let { "-$it" }.orEmpty()}.sit",
                    extension = "sit",
                )

                else -> error("Unsupported operating system: $name")
            }
        }
    }

    private fun writeJetBrainsClientArchive(path: Path, extension: String, buildNumber: String) {
        val productInfo = """
            {
                "name": "JetBrains Client",
                "version": "2025.1.6",
                "buildNumber": "$buildNumber",
                "productCode": "JBC"
            }
        """.trimIndent().toByteArray()

        path.parent.createDirectories()
        when (extension) {
            "tar.gz" -> writeTarGz(path, "product-info.json", productInfo)
            else -> writeZip(path, "product-info.json", productInfo)
        }
    }

    private fun writeZip(path: Path, name: String, content: ByteArray) {
        ZipOutputStream(Files.newOutputStream(path)).use {
            it.putNextEntry(ZipEntry(name))
            it.write(content)
            it.closeEntry()
        }
    }

    private fun writeTarGz(path: Path, name: String, content: ByteArray) {
        GZIPOutputStream(Files.newOutputStream(path)).use { gzip ->
            val header = ByteArray(512)
            fun write(offset: Int, length: Int, value: String) {
                value.toByteArray().copyInto(header, offset, endIndex = minOf(value.length, length))
            }

            write(0, 100, name)
            write(100, 8, "0000644")
            write(108, 8, "0000000")
            write(116, 8, "0000000")
            write(124, 12, content.size.toString(8).padStart(11, '0'))
            write(136, 12, "00000000000")
            repeat(8) { header[148 + it] = ' '.code.toByte() }
            header[156] = '0'.code.toByte()
            write(257, 6, "ustar")
            write(263, 2, "00")

            val checksum = header.sumOf { it.toUByte().toInt() }
            write(148, 6, checksum.toString(8).padStart(6, '0'))
            header[154] = 0
            header[155] = ' '.code.toByte()

            gzip.write(header)
            gzip.write(content)
            gzip.write(ByteArray((512 - content.size % 512) % 512))
            gzip.write(ByteArray(1024))
        }
    }

    private data class JetBrainsClientArtifact(
        val fileName: String,
        val extension: String,
    )
}
