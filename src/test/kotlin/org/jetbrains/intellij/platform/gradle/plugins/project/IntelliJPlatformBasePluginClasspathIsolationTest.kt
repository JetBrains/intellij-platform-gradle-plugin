// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.jetbrains.intellij.platform.gradle.assertNotContains
import kotlin.test.Test

class IntelliJPlatformBasePluginClasspathIsolationTest {

    @Test
    fun `classes loaded before Kotlin plugin application do not hard link Kotlin Gradle API`() {
        assertNoKotlinGradleApiLinks("org/jetbrains/intellij/platform/gradle/plugins/project/IntelliJPlatformBasePlugin.class")
        assertNoKotlinGradleApiLinks("org/jetbrains/intellij/platform/gradle/plugins/PluginsKt.class")
    }

    private fun assertNoKotlinGradleApiLinks(resourcePath: String) {
        val classBytes = requireNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Unable to load class bytes from $resourcePath"
        }.readBytes()
        val classContents = String(classBytes, Charsets.ISO_8859_1)

        assertNotContains("org/jetbrains/kotlin/gradle/tasks/KotlinJvmCompile", classContents)
        assertNotContains("org/jetbrains/kotlin/gradle/dsl/JvmTarget", classContents)
    }
}
