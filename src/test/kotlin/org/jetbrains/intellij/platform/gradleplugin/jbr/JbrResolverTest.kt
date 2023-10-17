// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.jbr

import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginSpecBase
import kotlin.test.Test

const val TASK_NAME = "testJbrResolver"

class JbrResolverTest : IntelliJPluginSpecBase() {

    private val operatingSystem = OperatingSystem.current()
    private val platform = ""//JetBrainsRuntimeResolver.JbrArtifact.platform(operatingSystem)
    private val arch = ""//JetBrainsRuntimeResolver.JbrArtifact.arch(true)

    @Test
    fun `resolve 11_0_11b1536_2`() = testJbrResolving("11_0_11b1536.2", "jbr_jcef-11_0_11-$platform-$arch-b1536.2")

    @Test
    fun `resolve jbrsdk-11_0_13b1751_16`() = testJbrResolving("jbrsdk-11_0_13b1751.16", "jbrsdk-11_0_13-$platform-$arch-b1751.16")

    @Test
    fun `resolve jbr_dcevm-11_0_13b1751_16`() = testJbrResolving("jbr_dcevm-11_0_13b1751.16", "jbr_dcevm-11_0_13-$platform-$arch-b1751.16")

    @Test
    fun `resolve jbr_fd-11_0_13b1751_16`() = testJbrResolving("jbr_fd-11_0_13b1751.16", "jbr_fd-11_0_13-$platform-$arch-b1751.16")

    @Test
    fun `resolve jbr_nomod-11_0_13b1751_16`() = testJbrResolving("jbr_nomod-11_0_13b1751.16", "jbr_nomod-11_0_13-$platform-$arch-b1751.16")

    @Test
    fun `resolve 11_0_13b1751_16 in fd variant`() = testJbrResolving("11_0_13b1751.16", "jbr_fd-11_0_13-$platform-$arch-b1751.16", "fd")

    private fun testJbrResolving(version: String, expected: String, variant: String? = null) {
        buildFile.groovy(
            """
            runIde {
                jbrVersion = "$version"
                ${"jbrVariant = \"$variant\"".takeIf { variant != null }.orEmpty()}
            }
                
            def projectExecutableProvider = tasks.named("runIde").flatMap { it.projectExecutable }
                        
            task $TASK_NAME {
                doLast {
                    println(projectExecutableProvider.get())
                }
            }
            """.trimIndent()
        )

        build(TASK_NAME).let {
            assertContains(expected, it.output)
            assertNotContains("Error when resolving dependency", it.output)
        }
    }
}
