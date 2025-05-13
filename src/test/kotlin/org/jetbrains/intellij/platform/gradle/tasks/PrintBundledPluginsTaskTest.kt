// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import kotlin.test.Test

class PrintBundledPluginsTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `print bundled plugins`() {
        build(Tasks.PRINT_BUNDLED_PLUGINS) {
            assertContains(
                """
                > Task :${Tasks.PRINT_BUNDLED_PLUGINS}
                Bundled plugins for IntelliJ IDEA 2022.3.3 (223.8836.41):
                AntSupport (Ant)
                ByteCodeViewer (Bytecode Viewer)
                Coverage (Code Coverage for Java)
                DevKit (Plugin DevKit)
                Git4Idea (Git)
                JUnit (JUnit)
                Lombook Plugin (Lombok)
                PerforceDirectPlugin (Perforce Helix Core)
                Subversion (Subversion)
                TestNG-J (TestNG)
                XPathView (XPathView + XSLT)
                XSLT-Debugger (XSLT Debugger)
                com.android.tools.design (Android Design Tools)
                com.android.tools.idea.smali (Smali Support)
                com.intellij (IDEA CORE)
                com.intellij.completion.ml.ranking (Machine Learning Code Completion)
                com.intellij.configurationScript (Configuration Script)
                com.intellij.copyright (Copyright)
                com.intellij.dev
                com.intellij.gradle (Gradle)
                com.intellij.java (Java)
                com.intellij.java-i18n (Java Internationalization)
                com.intellij.java.ide (Java IDE Customization)
                com.intellij.marketplace (JetBrains Marketplace Licensing Support)
                com.intellij.platform.images (Images)
                com.intellij.plugins.eclipsekeymap (Eclipse Keymap)
                com.intellij.plugins.netbeanskeymap (NetBeans Keymap)
                com.intellij.plugins.visualstudiokeymap (Visual Studio Keymap)
                com.intellij.properties (Properties)
                com.intellij.searcheverywhere.ml (Machine Learning in Search Everywhere)
                com.intellij.settingsSync (Settings Sync)
                com.intellij.tasks (Task Management)
                com.intellij.tracing.ide
                com.intellij.uiDesigner (UI Designer)
                com.jetbrains.codeWithMe (Code With Me)
                com.jetbrains.packagesearch.intellij-plugin (Package Search)
                com.jetbrains.projector.libs (Projector Libraries for Code With Me and Remote Development)
                com.jetbrains.sh (Shell Script)
                com.jetbrains.space (Space)
                hg4idea (Mercurial)
                intellij.indexing.shared.core (Shared Indexes)
                intellij.webp (WebP Support)
                org.editorconfig.editorconfigjetbrains (EditorConfig)
                org.intellij.groovy (Groovy)
                org.intellij.intelliLang (IntelliLang)
                org.intellij.plugins.markdown (Markdown)
                org.intellij.qodana (Qodana)
                org.jetbrains.android (Android)
                org.jetbrains.debugger.streams (Java Stream Debugger)
                org.jetbrains.idea.eclipse (Eclipse Interoperability)
                org.jetbrains.idea.gradle.dsl (Gradle DSL API)
                org.jetbrains.idea.maven (Maven)
                org.jetbrains.idea.maven.model (JetBrains maven model api classes)
                org.jetbrains.idea.maven.server.api (Maven server api classes)
                org.jetbrains.idea.reposearch (JetBrains Repository Search)
                org.jetbrains.java.decompiler (Java Bytecode Decompiler)
                org.jetbrains.kotlin (Kotlin)
                org.jetbrains.plugins.github (GitHub)
                org.jetbrains.plugins.gradle (Gradle-Java)
                org.jetbrains.plugins.gradle.dependency.updater (Gradle Dependency Updater Implementation)
                org.jetbrains.plugins.gradle.maven (Gradle-Maven)
                org.jetbrains.plugins.javaFX (JavaFX)
                org.jetbrains.plugins.terminal (Terminal)
                org.jetbrains.plugins.textmate (TextMate Bundles)
                org.jetbrains.plugins.yaml (YAML)
                org.toml.lang (Toml)
                tanvd.grazi (Grazie)
                training (IDE Features Trainer)
                """.trimIndent(),
                output,
            )
        }
    }
}
