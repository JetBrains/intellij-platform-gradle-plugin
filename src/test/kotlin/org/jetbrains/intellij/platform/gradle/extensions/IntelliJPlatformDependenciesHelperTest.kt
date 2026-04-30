// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.jetbrains.intellij.platform.gradle.models.IdeLayoutIndex
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test
import kotlin.test.assertEquals

class IntelliJPlatformDependenciesHelperTest {

    @Test
    fun `test runtime classpath includes product modules`() {
        val platformPath = Files.createTempDirectory("platform")
        platformPath.resolve("lib").createDirectories()
        platformPath.resolve("lib/product-backend.jar").createFile()
        platformPath.resolve("lib/intellij.profiler.asyncOne.jar").createFile()
        val productInfo = ProductInfo(
            layout = listOf(
                ProductInfo.LayoutItem(
                    name = "com.intellij",
                    kind = ProductInfo.LayoutItemKind.plugin,
                    classPath = listOf("lib/product-backend.jar"),
                ),
                ProductInfo.LayoutItem(
                    name = "intellij.profiler.asyncOne",
                    kind = ProductInfo.LayoutItemKind.productModuleV2,
                    classPath = listOf("lib/intellij.profiler.asyncOne.jar"),
                ),
                ProductInfo.LayoutItem(
                    name = "intellij.platform.embedded",
                    kind = ProductInfo.LayoutItemKind.productModuleV2,
                    classPath = listOf("lib/intellij.platform.embedded.jar"),
                ),
                ProductInfo.LayoutItem(
                    name = "intellij.vcs.git.model.generated",
                    kind = ProductInfo.LayoutItemKind.moduleV2,
                    classPath = listOf("plugins/cwm-plugin/lib/modules/intellij.vcs.git.model.generated.jar"),
                ),
                ProductInfo.LayoutItem(
                    name = "Git4Idea",
                    kind = ProductInfo.LayoutItemKind.plugin,
                    classPath = listOf("plugins/git4idea/lib/git4idea.jar"),
                ),
            ),
        )

        assertEquals(
            listOf(
                platformPath.resolve("lib/product-backend.jar"),
                platformPath.resolve("lib/intellij.profiler.asyncOne.jar"),
            ).map { it.invariantSeparatorsPathString }.toSet(),
            productInfo.testRuntimeClasspath(platformPath),
        )
    }

    @Test
    fun `plugin without original file falls back to classpath publications`() {
        val platformPath = Files.createTempDirectory("platform")
        val entry = IdeLayoutIndex.Entry(
            key = "entry-1",
            id = "com.example.plugin",
            classpath = listOf(
                "plugins/example/lib/example.jar",
                "plugins/example/lib/example-dependency.jar",
            ),
        )

        assertEquals(
            listOf(
                platformPath.resolve("plugins/example/lib/example.jar"),
                platformPath.resolve("plugins/example/lib/example-dependency.jar"),
            ).map { it.invariantSeparatorsPathString },
            entry.resolvePublicationPaths(platformPath).map { it.invariantSeparatorsPathString },
        )
    }

    @Test
    fun `plugin with original file keeps archive publication`() {
        val platformPath = Files.createTempDirectory("platform")
        val entry = IdeLayoutIndex.Entry(
            key = "entry-1",
            id = "com.example.plugin",
            originalFile = "plugins/example",
            classpath = listOf("plugins/example/lib/example.jar"),
        )

        assertEquals(
            listOf(platformPath.resolve("plugins/example").invariantSeparatorsPathString),
            entry.resolvePublicationPaths(platformPath).map { it.invariantSeparatorsPathString },
        )
    }

    @Test
    fun `module publications use classpath entries`() {
        val platformPath = Files.createTempDirectory("platform")
        val entry = IdeLayoutIndex.Entry(
            key = "entry-1",
            id = "com.example.module",
            isModule = true,
            originalFile = "plugins/example",
            classpath = listOf("plugins/example/lib/module.jar"),
        )

        assertEquals(
            listOf(platformPath.resolve("plugins/example/lib/module.jar").invariantSeparatorsPathString),
            entry.resolvePublicationPaths(platformPath).map { it.invariantSeparatorsPathString },
        )
    }
}
