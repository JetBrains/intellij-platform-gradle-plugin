// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.test.Test

class PrintBundledPluginsTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `print bundled plugins`() {
        build(Tasks.PRINT_BUNDLED_PLUGINS) {
            assertContains(
                """
                > Task :${Tasks.PRINT_BUNDLED_PLUGINS}
                AntSupport
                ByteCodeViewer
                Coverage
                DevKit
                Git4Idea
                JUnit
                Lombook Plugin
                PerforceDirectPlugin
                Subversion
                TestNG-J
                XPathView
                XSLT-Debugger
                com.android.tools.design
                com.android.tools.idea.smali
                com.intellij
                com.intellij.completion.ml.ranking
                com.intellij.configurationScript
                com.intellij.copyright
                com.intellij.dev
                com.intellij.gradle
                com.intellij.java
                com.intellij.java-i18n
                com.intellij.java.ide
                com.intellij.marketplace
                com.intellij.platform.images
                com.intellij.plugins.eclipsekeymap
                com.intellij.plugins.netbeanskeymap
                com.intellij.plugins.visualstudiokeymap
                com.intellij.properties
                com.intellij.searcheverywhere.ml
                com.intellij.settingsSync
                com.intellij.tasks
                com.intellij.tracing.ide
                com.intellij.uiDesigner
                com.jetbrains.codeWithMe
                com.jetbrains.packagesearch.intellij-plugin
                com.jetbrains.projector.libs
                com.jetbrains.sh
                com.jetbrains.space
                hg4idea
                intellij.indexing.shared.core
                intellij.webp
                org.editorconfig.editorconfigjetbrains
                org.intellij.groovy
                org.intellij.intelliLang
                org.intellij.plugins.markdown
                org.intellij.qodana
                org.jetbrains.android
                org.jetbrains.debugger.streams
                org.jetbrains.idea.eclipse
                org.jetbrains.idea.gradle.dsl
                org.jetbrains.idea.maven
                org.jetbrains.idea.maven.model
                org.jetbrains.idea.maven.server.api
                org.jetbrains.idea.reposearch
                org.jetbrains.java.decompiler
                org.jetbrains.kotlin
                org.jetbrains.plugins.github
                org.jetbrains.plugins.gradle
                org.jetbrains.plugins.gradle.dependency.updater
                org.jetbrains.plugins.gradle.maven
                org.jetbrains.plugins.javaFX
                org.jetbrains.plugins.terminal
                org.jetbrains.plugins.textmate
                org.jetbrains.plugins.yaml
                org.toml.lang
                tanvd.grazi
                training
                """.trimIndent(),
                output,
            )
        }
    }
}
