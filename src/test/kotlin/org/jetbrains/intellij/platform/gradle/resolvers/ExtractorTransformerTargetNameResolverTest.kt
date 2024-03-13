// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExtractorTransformerTargetNameResolverTest : IntelliJPluginTestBase() {

    @Test
    fun `resolve name for JetBrains Marketplace plugin dependency`() {
        val path =
            Path("/Users/hsz/.gradle/caches/modules-2/files-2.1/com.jetbrains.plugins/org.intellij.plugins.markdown/223.8617.3/8e6bdd458f8ac482e95e91863a2bdb9d31add95c/org.intellij.plugins.markdown-223.8617.3.zip")
        val resolvedName = createResolver(path).resolve()

        assertEquals("com.jetbrains.plugins-org.intellij.plugins.markdown-223.8617.3", resolvedName)
    }

    @Test
    fun `resolve name for IntelliJ Platform dependency`() {
        val path =
            Path("/Users/hsz/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2022.3.3/19e52733ac61e1d2e675720f92daf5959355cb1e/ideaIC-2022.3.3.zip")
        val resolvedName = createResolver(path).resolve()

        assertEquals("IC-2022.3.3", resolvedName)
    }

    @Test
    fun `resolve name for JetBrains Runtime dependency`() {
        val path =
            Path("/Users/hsz/.gradle/caches/modules-2/files-2.1/com.jetbrains/jbr/jbr_jcef-17.0.10-osx-aarch64-b1087.21/68c504dcd9ecc37273c2c9973ef02f06ca252eb0/jbr-jbr_jcef-17.0.10-osx-aarch64-b1087.21.tar.gz")
        val resolvedName = createResolver(path).resolve()

        assertEquals("jbr_jcef-17.0.10-osx-aarch64-b1087.21", resolvedName)
    }

    @Test
    fun `fails for an empty path`() {
        assertFailsWith<GradleException>("Cannot resolve 'Extractor Transformer Target Name'") {
            createResolver(Path("")).resolve()
        }
    }

    @Test
    fun `fails for an incorrect path`() {
        assertFailsWith<GradleException>("Cannot resolve 'Extractor Transformer Target Name'") {
            createResolver(Path("/tmp/foo")).resolve()
        }
    }

    private fun createResolver(artifactPath: Path) = ExtractorTransformerTargetNameResolver(artifactPath)
}
