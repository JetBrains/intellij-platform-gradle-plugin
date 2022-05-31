// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.jbr

import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.IntelliJPluginSpecBase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

const val TASK_NAME = "testJbrResolver"

open class JbrResolverTest : IntelliJPluginSpecBase() {

    private val operatingSystem = OperatingSystem.current()
    private val platform = JbrResolver.JbrArtifact.platform(operatingSystem)
    private val arch = JbrResolver.JbrArtifact.arch(true)

    @Test
    fun `resolve 11_0_2b159`() = testJbrResolving("11_0_2b159", "jbr-11_0_2-$platform-$arch-b159")

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
        buildFile.groovy("""
            runIde {
                jbrVersion = "$version"
                ${"jbrVariant = \"$variant\"".takeIf { variant != null }}
            }
            task $TASK_NAME {
                doLast {
                    println(runIde.projectExecutable.get())
                }
            }
        """)

        val output = build(TASK_NAME).output
        output.apply {
            assertTrue(this) {
                contains(expected)
            }
            assertFalse(this) {
                contains("Error when resolving dependency")
            }
        }
    }
}
