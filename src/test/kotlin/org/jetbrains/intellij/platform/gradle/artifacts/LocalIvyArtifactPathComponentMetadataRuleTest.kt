// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalIvyArtifactPathComponentMetadataRuleTest {

    @Test
    fun `decode ivy publications with dependency artifacts`() {
        val input = """
            <ivy-module version="2.0">
              <info organisation="bundledPlugin" module="org.intellij.groovy" revision="IU-253.28294.334" />
              <publications>
                <artifact name="groovy-psi" ext="jar" conf="default" url="plugins/Groovy/lib" />
              </publications>
              <dependencies>
                <dependency org="bundledPlugin" name="com.intellij.java" rev="IU-253.28294.334">
                  <artifact name="java-dependency-fragment" type="jar" ext="jar" />
                </dependency>
              </dependencies>
            </ivy-module>
        """.trimIndent()

        val result = decodeIvyModulePublications(input)

        assertEquals(1, result.size)
        assertEquals("groovy-psi", result.single().name)
        assertEquals("plugins/Groovy/lib", result.single().url)
    }
}
