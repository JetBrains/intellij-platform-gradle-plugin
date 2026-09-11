// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.decodeFromString
import org.gradle.api.initialization.resolve.RulesMode
import org.gradle.testfixtures.ProjectBuilder
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IvyModuleTest {

    private val input = """
        <ivy-module version="2.0">
          <info organisation="bundledPlugin" module="org.jetbrains.kotlin" revision="IU-253.28294.334" />
          <configurations>
            <conf name="default" visibility="public" />
          </configurations>
          <publications>
            <artifact name="kotlin-base-jps" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="vavr" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlin-plugin-shared" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.kotlin-compiler-fe10" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.parcelize-compiler-plugin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.compose-compiler-plugin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="javax-inject" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlin-gradle-tooling" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="completion-ranking-kotlin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.scripting-compiler-plugin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.assignment-compiler-plugin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.allopen-compiler-plugin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.js-plain-objects-compiler-plugin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="jackson-dataformat-toml" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.kotlin-dataframe-compiler-plugin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.noarg-compiler-plugin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.sam-with-receiver-compiler-plugin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.lombok-compiler-plugin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.kotlin-compiler-ir" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.kotlinx-serialization-compiler-plugin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="ehcache.sizeof" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlin-plugin" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.kotlin-jps-common" ext="jar" conf="default" url="plugins/Kotlin/lib" />
            <artifact name="kotlinc.kotlin-compiler-common" ext="jar" conf="default" url="plugins/Kotlin/lib" />
          </publications>
          <dependencies>
            <dependency org="bundledModule" name="intellij.platform.ml.impl" rev="IU-253.28294.334" />
            <dependency org="bundledModule" name="intellij.platform.collaborationTools" rev="IU-253.28294.334" />
            <dependency org="bundledModule" name="intellij.platform.scriptDebugger.ui" rev="IU-253.28294.334" />
            <dependency org="bundledPlugin" name="com.intellij.java" rev="IU-253.28294.334" />
          </dependencies>
        </ivy-module>
    """.trimIndent()

    @Test
    fun `decode bundled plugin ivy module`() {
        val result = xml.decodeFromString<IvyModule>(input)

        assertEquals("bundledPlugin", result.info?.organisation)
        assertEquals("org.jetbrains.kotlin", result.info?.module)
        assertEquals(24, result.publications.size)
        assertEquals(4, result.dependencies.size)
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `plugin source artifacts use the Ivy sources configuration in every rules mode`() {
        val pluginPath = createTempDirectory("plugin")

        try {
            pluginPath.resolve("lib/src/plugin-api-sources.jar").apply {
                parent.createDirectories()
                createFile()
            }
            val project = ProjectBuilder.builder().build()

            val relativeArtifact = pluginPath.toIvySourceArtifacts(
                project.provider { RulesMode.PREFER_PROJECT },
                pluginPath.parent,
            ).single()
            val absoluteArtifact = pluginPath.toIvySourceArtifacts(
                project.provider { RulesMode.PREFER_SETTINGS },
                pluginPath.parent,
            ).single()

            assertEquals(IVY_SOURCES_CONFIGURATION, relativeArtifact.conf)
            assertEquals("plugin-api-sources", relativeArtifact.name)
            assertEquals("${pluginPath.name}/lib/src", relativeArtifact.url)
            assertEquals(IVY_SOURCES_CONFIGURATION, absoluteArtifact.conf)
            assertNull(absoluteArtifact.url)
            assertTrue(absoluteArtifact.name.orEmpty().endsWith("${pluginPath.name}/lib/src/plugin-api-sources.jar"))
        } finally {
            pluginPath.deleteRecursively()
        }
    }
}
