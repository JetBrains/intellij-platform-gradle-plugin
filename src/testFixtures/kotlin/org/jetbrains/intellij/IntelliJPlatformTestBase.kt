// Copyright 2022-2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.gradle.internal.impldep.org.testng.annotations.BeforeTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import java.io.File
import java.nio.file.Files.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse

abstract class IntelliJPlatformTestBase {

    var debugEnabled = true
    val gradleDefault = System.getProperty("test.gradle.default")
    val gradleScan = System.getProperty("test.gradle.scan").toBoolean()
    val gradleArguments = System.getProperty("test.gradle.arguments", "").split(' ').filter(String::isNotEmpty).toMutableList()
    val kotlinPluginVersion: String = System.getProperty("test.kotlin.version")
    val gradleVersion: String = System.getProperty("test.gradle.version").takeUnless { it.isNullOrEmpty() } ?: gradleDefault

    val gradleHome: String = System.getProperty("test.gradle.home")
    var dir = createTempDirectory("tmp").toFile()

    @BeforeTest
    open fun setup() {
        dir = createTempDirectory("tmp").toFile()
    }

    protected fun build(vararg tasksList: String) = build(
        tasks = tasksList,
    )

    protected fun buildAndFail(vararg tasksList: String) = build(
        fail = true,
        tasks = tasksList,
    )

    protected fun build(
        gradleVersion: String = this.gradleVersion,
        fail: Boolean = false,
        assertValidConfigurationCache: Boolean = true,
        vararg tasks: String,
    ): BuildResult = builder(gradleVersion, *tasks)
        .run {
            when (fail) {
                true -> buildAndFail()
                false -> build()
            }
        }
        .also {
            if (assertValidConfigurationCache) {
                assertNotContains("Configuration cache problems found in this build.", it.output)
            }
        }

    private fun builder(gradleVersion: String, vararg tasks: String) =
        GradleRunner.create()
            .withProjectDir(dir)
            .withGradleVersion(gradleVersion)
            .forwardOutput()
//            .withPluginClasspath()
            .withDebug(debugEnabled)
            .withTestKitDir(File(gradleHome))
            .withArguments(
                *tasks,
                *listOfNotNull(
                    "--stacktrace",
                    "--configuration-cache",
                    "--scan".takeIf { gradleScan },
                ).toTypedArray(),
                *gradleArguments.toTypedArray()
            )//, "-Dorg.gradle.debug=true")
    protected fun assertNotContains(expected: String, actual: String) {
        // https://stackoverflow.com/questions/10934743/formatting-output-so-that-intellij-idea-shows-diffs-for-two-texts
        assertFalse(
            actual.contains(expected),
            """
            expected:<$expected> but was:<$actual>
            """.trimIndent()
        )
    }

    protected fun assertFileContent(file: File?, @Language("xml") expectedContent: String) =
        assertEquals(expectedContent.trim(), file?.readText()?.replace("\r", "")?.trim())
}
