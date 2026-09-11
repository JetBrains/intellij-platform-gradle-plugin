// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifyPluginSignatureTaskArgumentsTest {

    private val certificateChainContent =
        """
        -----BEGIN CERTIFICATE-----
        MIIFakeCertificateContentUsedOnlyForArgumentConstructionTests==
        -----END CERTIFICATE-----
        """.trimIndent()

    @Test
    fun `certificateChain passed as content yields a single -cert option pointing to a file`() {
        val arguments = buildArguments {
            certificateChain.set(certificateChainContent)
        }

        val certOptionIndices = arguments.withIndex().filter { it.value == "-cert" }.map { it.index }
        assertEquals(1, certOptionIndices.size, "Expected exactly one '-cert' option, got: $arguments")

        val certValue = arguments[certOptionIndices.single() + 1]
        assertTrue(certValue.endsWith(".pem"), "Expected '-cert' to point to a .pem file, got: $certValue")
        assertTrue(Path.of(certValue).exists(), "Expected '-cert' file to exist: $certValue")
        assertEquals(certificateChainContent, Path.of(certValue).readText())

        assertFalse(
            arguments.any { it.contains("-----BEGIN CERTIFICATE-----") },
            "Raw certificate content must not be passed as a CLI argument, got: $arguments",
        )
    }

    @Test
    fun `certificateChain passed as file yields a single -cert option pointing to that file`() {
        val projectDir = createTempDirectory("verify-plugin-signature-arguments-test")
        val certificateFile = projectDir.resolve("chain.crt").also { it.writeText(certificateChainContent) }

        val arguments = buildArguments(projectDir) {
            certificateChainFile.set(certificateFile.toFile())
        }

        val certOptionIndices = arguments.withIndex().filter { it.value == "-cert" }.map { it.index }
        assertEquals(1, certOptionIndices.size, "Expected exactly one '-cert' option, got: $arguments")
        assertEquals(certificateFile.invariantSeparatorsPathString, arguments[certOptionIndices.single() + 1])
    }

    private fun buildArguments(
        projectDir: Path = createTempDirectory("verify-plugin-signature-arguments-test"),
        configure: VerifyPluginSignatureTask.() -> Unit,
    ): List<String> {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        val archiveFile = projectDir.resolve("plugin.zip").also { it.writeText("dummy") }

        val task = project.tasks.register("verifyPluginSignatureUnderTest", VerifyPluginSignatureTask::class.java).get()
        task.inputArchiveFile.set(archiveFile.toFile())
        task.configure()

        val field = VerifyPluginSignatureTask::class.java.getDeclaredField("arguments").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        return (field.get(task) as Sequence<String>).toList()
    }
}
