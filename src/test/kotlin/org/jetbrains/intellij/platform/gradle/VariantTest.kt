// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VariantTest {

    @Test
    fun `resolve current native variant`() {
        mapOf(
            ("Linux" to "amd64") to Variant("linux", "x86_64"),
            ("Linux" to "aarch64") to Variant("linux", "arm64"),
            ("Mac OS X" to "x86_64") to Variant("mac", "x86_64"),
            ("Darwin" to "arm64") to Variant("mac", "arm64"),
            ("Windows 11" to "x64") to Variant("windows", "x86_64"),
            ("Windows 11" to "arm64") to Variant("windows", "arm64"),
        ).forEach { (system, expected) ->
            assertEquals(expected, currentVariant(system.first, system.second))
        }
    }

    @Test
    fun `reject unsupported current native variant`() {
        assertFailsWith<GradleException> {
            currentVariant("FreeBSD", "x86_64")
        }
        assertFailsWith<GradleException> {
            currentVariant("Linux", "riscv64")
        }
    }
}
