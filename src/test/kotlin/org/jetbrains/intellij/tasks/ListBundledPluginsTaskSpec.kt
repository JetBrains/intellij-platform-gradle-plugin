// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants.LIST_BUNDLED_PLUGINS_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginSpecBase
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("ComplexRedundantLet")
class ListBundledPluginsTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `list plugins for 2020_1`() {
        buildFile.groovy(
            """
            intellij {
                version = "2020.1"
            }
            """.trimIndent()
        )

        build(LIST_BUNDLED_PLUGINS_TASK_NAME)

        val content = buildDirectory.resolve("$LIST_BUNDLED_PLUGINS_TASK_NAME.txt").readText()
        assertEquals(
            """
                org.jetbrains.idea.reposearch
                AntSupport
                ByteCodeViewer
                org.jetbrains.java.decompiler
                org.intellij.intelliLang
                org.jetbrains.debugger.streams
                Git4Idea
                com.intellij.tasks
                com.intellij.stats.completion
                com.intellij.laf.macos
                org.jetbrains.idea.eclipse
                org.jetbrains.plugins.javaFX
                XPathView
                org.jetbrains.plugins.gradle.maven
                JUnit
                org.intellij.plugins.markdown
                com.intellij.java-i18n
                org.jetbrains.idea.maven
                TestNG-J
                org.jetbrains.settingsRepository
                com.jetbrains.changeReminder
                com.intellij.properties.bundle.editor
                org.jetbrains.plugins.textmate
                com.jetbrains.filePrediction
                org.jetbrains.plugins.terminal
                com.intellij.java
                org.jetbrains.kotlin
                com.intellij.platform.images
                hg4idea
                com.intellij.gradle
                com.jetbrains.sh
                org.editorconfig.editorconfigjetbrains
                org.jetbrains.plugins.github
                com.intellij.properties
                org.jetbrains.plugins.gradle
                org.jetbrains.android
                com.intellij.laf.win10
                org.jetbrains.plugins.yaml
                tanvd.grazi
                Subversion
                DevKit
                com.intellij.copyright
                org.intellij.groovy
                com.intellij.uiDesigner
                XSLT-Debugger
                com.intellij.java.ide
                com.android.tools.idea.smali
                Coverage
                com.intellij.configurationScript
            """.trimIndent(),
            content,
        )
    }

    @Test
    fun `reuse configuration cache`() {
        build(LIST_BUNDLED_PLUGINS_TASK_NAME)
        build(LIST_BUNDLED_PLUGINS_TASK_NAME).let {
            assertContains("Reusing configuration cache.", it.output)
        }
    }
}
