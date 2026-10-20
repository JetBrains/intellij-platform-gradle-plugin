// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginDescriptorTest {

    @Test
    fun `decode empty descriptor with optional fields`() {
        val descriptor = decode<PluginDescriptor>("<idea-plugin/>")

        assertNull(descriptor.id)
        assertNull(descriptor.ideaVersion)
        assertNull(descriptor.vendor)
        assertNull(descriptor.dependencies)
        assertNull(descriptor.content)
        assertTrue(descriptor.depends.isEmpty())
        assertTrue(descriptor.modules.isEmpty())
    }

    @Test
    fun `decode descriptor into typed metadata and dependency models`() {
        val descriptor = decode<PluginDescriptor>("""
            <idea-plugin package="example.plugin">
              <id>example.id</id><name>Example</name><version>1.2.3</version>
              <idea-version since-build="261" until-build="261.*"/>
              <vendor email="plugins@example.invalid" url="https://example.invalid">Vendor</vendor>
              <product-descriptor code="EXAMPLE" release-date="20261020" release-version="20261"/>
              <depends optional="true" config-file="optional.xml">legacy.plugin</depends>
              <dependencies><plugin id="modern.plugin"/><module name="modern.module"/></dependencies>
              <module value="legacy.module"/>
              <extensions><id>not.the.plugin.id</id></extensions>
            </idea-plugin>
        """.trimIndent())

        assertEquals("example.plugin", descriptor.packageName)
        assertEquals("example.id", descriptor.id)
        assertEquals("Example", descriptor.name)
        assertEquals("1.2.3", descriptor.version)
        assertEquals(PluginDescriptor.IdeaVersion("261", "261.*"), descriptor.ideaVersion)
        assertEquals(PluginDescriptor.Vendor("plugins@example.invalid", "https://example.invalid", "Vendor"), descriptor.vendor)
        assertEquals(PluginDescriptor.ProductDescriptor("EXAMPLE", "20261020", "20261"), descriptor.productDescriptor)
        assertEquals(listOf(PluginDescriptor.LegacyDependency("true", "optional.xml", "legacy.plugin")), descriptor.depends)
        assertEquals(listOf(PluginDescriptor.Dependency(id = "modern.plugin")), descriptor.dependencies?.plugins)
        assertEquals(listOf(PluginDescriptor.Dependency(name = "modern.module")), descriptor.dependencies?.modules)
        assertEquals(listOf(PluginDescriptor.ModuleAlias("legacy.module")), descriptor.modules)
    }

    @Test
    fun `decode mixed content without consuming following fields`() {
        val descriptor = decode<PluginDescriptor>("""
            <idea-plugin>
              <description>Before <![CDATA[<code>]]><b>bold <i>&amp; italic</i></b> after</description>
              <change-notes><![CDATA[<p>Changes</p>]]></change-notes>
              <id>example.id</id>
            </idea-plugin>
        """.trimIndent())

        assertEquals("Before <code>bold & italic after", descriptor.description?.value)
        assertEquals("<p>Changes</p>", descriptor.changeNotes?.value)
        assertEquals("example.id", descriptor.id)
    }

    @Test
    fun `unknown inline content does not consume following module dependencies`() {
        val descriptor = decode<PluginDescriptor>("""
            <idea-plugin>
              <content>
                <module name="named.module"><!-- not inline --></module>
                <module name="inline.module">
                  <extensions><custom implementation="example.Extension"/></extensions>
                  <dependencies><plugin id="inline.plugin"/></dependencies>
                </module>
                <module name="extension.only"><extensions/></module>
                <module name="empty.dependencies"><dependencies/></module>
              </content>
            </idea-plugin>
        """.trimIndent())
        val modules = descriptor.content!!.modules

        assertFalse(modules[0].inline)
        assertTrue(modules[1].inline)
        assertEquals(listOf(PluginDescriptor.Dependency(id = "inline.plugin")), modules[1].dependencies?.plugins)
        assertTrue(modules[2].inline)
        assertTrue(modules[3].inline)
    }
}