// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import org.gradle.api.artifacts.Dependency
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class MatrixMetadataTest {

    private val project = ProjectBuilder.builder().build()

    @Test
    fun `dependency metadata does not evaluate deferred declarations`() {
        val parent = project.configurations.create("parent")
        val child = project.configurations.create("child").extendsFrom(parent)
        parent.dependencies.add(project.dependencies.create("example:parent-plugin:1.0"))
        child.dependencies.add(project.dependencies.create("example:child-plugin:2.0"))
        val declarations = project.objects.listProperty(Dependency::class.java)
        declarations.addAll(project.provider<List<Dependency>> { error("Deferred dependency lists must not be evaluated") })
        parent.dependencies.addAllLater(declarations)
        child.dependencies.addLater(project.provider<Dependency> { error("Deferred dependencies must not be evaluated") })

        assertEquals(
            listOf(
                MatrixDependency("child", "example", "child-plugin", "2.0"),
                MatrixDependency("parent", "example", "parent-plugin", "1.0"),
            ),
            MatrixMetadata(project).dependencies(listOf("child", "parent", "missing")),
        )
    }

    @Test
    fun `dependency metadata includes already materialized provider declarations`() {
        val configuration = project.configurations.create("plugins")
        val dependency = project.dependencies.create("example:plugin:1.0")
        configuration.dependencies.addAllLater(project.provider { listOf(dependency) })
        assertEquals(listOf(dependency), configuration.dependencies.toList())

        assertEquals(
            listOf(MatrixDependency("plugins", "example", "plugin", "1.0")),
            MatrixMetadata(project).dependencies(listOf("plugins")),
        )
    }
}