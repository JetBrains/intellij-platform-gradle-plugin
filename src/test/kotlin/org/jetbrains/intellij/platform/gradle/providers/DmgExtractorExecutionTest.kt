// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.jvm.Jvm
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DmgExtractorExecutionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val commands = mutableListOf<List<String>>()
    private lateinit var target: Path

    @Test
    fun `reject a failed copy and unmount the image without retaining partial output`() {
        val extractor = extractor(copyExitCode = 23)

        val error = assertFailsWith<GradleException> { extractor.obtain() }

        assertTrue(error.message.orEmpty().contains("non-zero exit value 23"), error.message)
        assertEquals(listOf("hdiutil", "detach"), commands.last().take(2))
        assertFalse(target.exists())
    }

    @Test
    fun `preserve the copy failure when unmounting also fails`() {
        val extractor = extractor(copyExitCode = 23, detachExitCode = 16)

        val error = assertFailsWith<GradleException> { extractor.obtain() }

        assertTrue(error.message.orEmpty().contains("non-zero exit value 23"), error.message)
        assertTrue(error.suppressed.single().message.orEmpty().contains("non-zero exit value 16"))
        assertFalse(target.exists())
    }

    @Test
    fun `report an unmount failure after a successful copy`() {
        val extractor = extractor(detachExitCode = 16)

        val error = assertFailsWith<GradleException> { extractor.obtain() }

        assertTrue(error.message.orEmpty().contains("non-zero exit value 16"), error.message)
        assertEquals("copied content", target.resolve("copied.txt").readText())
    }

    @Test
    fun `return the copied files after unmounting successfully`() {
        val extractor = extractor()

        assertEquals(target, extractor.obtain())

        assertEquals("copied content", target.resolve("copied.txt").readText())
        assertEquals(listOf("hdiutil", "detach"), commands.last().take(2))
    }

    private fun extractor(copyExitCode: Int = 0, detachExitCode: Int = 0): DmgExtractorValueSource {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build() as ProjectInternal
        val delegate = project.services.get(ExecOperations::class.java)
        val executable = Jvm.current().javaExecutable.absolutePath
        val command = temporaryFolder.newFile("DmgCommand.java").toPath()
        command.writeText(
            //language=java
            """
            import java.nio.file.Files;
            import java.nio.file.Path;

            class DmgCommand {
                public static void main(String[] args) throws Exception {
                    if (args[0].equals("rsync")) {
                        Files.writeString(Path.of(args[args.length - 1]).resolve("copied.txt"), "copied content");
                        System.exit($copyExitCode);
                    } else if (args[1].equals("info")) {
                        System.out.println("<?xml version=\"1.0\"?><plist version=\"1.0\"><dict><key>images</key><array/></dict></plist>");
                    } else if (args[1].equals("detach")) {
                        System.exit($detachExitCode);
                    }
                }
            }
            """.trimIndent(),
        )
        target = temporaryFolder.newFolder("extracted").toPath()
        val parameters = object : DmgExtractorValueSource.Parameters {
            override val path = project.objects.fileProperty().fileValue(temporaryFolder.newFile("idea.dmg"))
            override val target = project.objects.directoryProperty().fileValue(this@DmgExtractorExecutionTest.target.toFile())
        }

        return object : DmgExtractorValueSource() {
            override fun getParameters() = parameters

            override val execOperations = object : ExecOperations {
                override fun exec(action: Action<in ExecSpec>) = delegate.exec {
                    action.execute(this)
                    commands.add(commandLine.map { it.toString() })
                    commandLine = listOf(executable, command.toString()) + commandLine
                }

                override fun javaexec(action: Action<in JavaExecSpec>) = delegate.javaexec(action)
            }
        }
    }
}