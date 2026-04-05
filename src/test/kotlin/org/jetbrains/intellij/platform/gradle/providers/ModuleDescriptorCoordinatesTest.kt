// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.ModuleDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModuleDescriptorCoordinatesTest {

    @Test
    fun `ignore legacy library descriptor names without module prefix`() {
        val descriptor = ModuleDescriptor(
            name = "jaxb-api",
            namespace = "\$legacy_jps_library",
            visibility = "public",
            dependencies = emptyList(),
            resources = resources("../lib/jaxb-api.jar"),
        )

        assertNull(descriptor.toCoordinatesOrNull())
    }

    @Test
    fun `convert jps module descriptor into coordinates`() {
        val descriptor = ModuleDescriptor(
            name = "intellij.platform.codeStyle",
            namespace = "jps",
            visibility = "public",
            dependencies = emptyList(),
            resources = resources("../lib/codeStyle.jar"),
        )

        assertEquals(
            Coordinates("com.jetbrains.intellij.platform", "code-style"),
            descriptor.toCoordinatesOrNull(),
        )
    }

    @Test
    fun `convert descriptor without namespace into coordinates`() {
        val descriptor = ModuleDescriptor(
            name = "intellij.platform.boot",
            namespace = null,
            visibility = "public",
            dependencies = emptyList(),
            resources = resources("../lib/platform-loader.jar"),
        )

        assertEquals(
            Coordinates("com.jetbrains.intellij.platform", "boot"),
            descriptor.toCoordinatesOrNull(),
        )
    }

    private fun resources(path: String) = ModuleDescriptor.Resources(
        resourceRoot = ModuleDescriptor.Resources.ResourceRoot(path = path),
    )
}
