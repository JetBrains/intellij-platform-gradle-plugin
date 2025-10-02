// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")

version = "1.0.0"

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(17)
}

// Disable dependency verification for these configuration if signature verification is being used (pgp option),
// because they break Gradle's logic for signature generation with a very obscure error: "Invalid UTF-8 input".
// There seems to be a few bugs in Gradle related to this error and also for generating a signature for JDK.
// https://github.com/gradle/gradle/issues?q=%22Invalid+UTF-8+input%22
// https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1779#issuecomment-2384461002
configurations {
    listOf(
        "compileClasspath",
        "compileOnlyDependenciesMetadata",
        "intellijPlatformClasspath",
        "intellijPlatformDependency",
        "intellijPlatformDependencyArchive",
        "testCompileOnlyDependenciesMetadata",
    ).forEach {
        named(it) {
            resolutionStrategy.disableDependencyVerification()
        }
    }
}

repositories {
    mavenCentral()

    intellijPlatform {
        //jetbrainsRuntime()
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
        //instrumentationTools()
        testFramework(TestFrameworkType.Platform)

        // This is important for bug reproduction because we need some dependencies in the test
        // Use ./gradlew printBundledPlugins
        bundledPlugins(
            "ByteCodeViewer",
            "Coverage",
            "Git4Idea",
            "HtmlTools",
            "JUnit",
            "PerforceDirectPlugin",
            "Subversion",
            "TestNG-J",
            "com.android.tools.gradle.dcl",
            "com.intellij",
            "com.intellij.completion.ml.ranking",
            "com.intellij.compose",
            "com.intellij.configurationScript",
            "com.intellij.copyright",
            "com.intellij.dev",
            "com.intellij.gradle",
            "com.intellij.ja",
            "com.intellij.java",
            "com.intellij.java-i18n",
            "com.intellij.java.ide",
            "com.intellij.ko",
            "com.intellij.marketplace",
            "com.intellij.marketplace.ml",
            "com.intellij.modules.json",
            "com.intellij.notebooks.core",
            "com.intellij.platform.images",
            "com.intellij.plugins.eclipsekeymap",
            "com.intellij.plugins.netbeanskeymap",
            "com.intellij.plugins.visualstudiokeymap",
            "com.intellij.properties",
            "com.intellij.searcheverywhere.ml",
            "com.intellij.settingsSync",
            "com.intellij.tasks",
            "com.intellij.turboComplete",
            "com.intellij.zh",
            "com.jetbrains.codeWithMe",
            "com.jetbrains.performancePlugin",
            "com.jetbrains.performancePlugin.async",
            "com.jetbrains.sh",
            "com.jetbrains.station",
            "hg4idea",
            "intellij.git.commit.modal",
            "intellij.indexing.shared.core",
            "intellij.jupyter",
            "intellij.platform.ijent.impl",
            "intellij.webp",
            "org.editorconfig.editorconfigjetbrains",
            "org.intellij.groovy",
            "org.intellij.intelliLang",
            "org.intellij.plugins.markdown",
            "org.intellij.qodana",
            "org.jetbrains.completion.full.line",
            "org.jetbrains.debugger.streams",
            "org.jetbrains.idea.eclipse",
            "org.jetbrains.idea.gradle.dsl",
            "org.jetbrains.idea.maven",
            "org.jetbrains.idea.maven.model",
            "org.jetbrains.idea.maven.server.api",
            "org.jetbrains.idea.reposearch",
            "org.jetbrains.java.decompiler",
            "org.jetbrains.kotlin",
            "org.jetbrains.plugins.github",
            "org.jetbrains.plugins.gitlab",
            "org.jetbrains.plugins.gradle",
            "org.jetbrains.plugins.gradle.analysis",
            "org.jetbrains.plugins.gradle.dependency.updater",
            "org.jetbrains.plugins.gradle.maven",
            "org.jetbrains.plugins.javaFX",
            "org.jetbrains.plugins.kotlin.jupyter",
            "org.jetbrains.plugins.terminal",
            "org.jetbrains.plugins.textmate",
            "org.jetbrains.plugins.yaml",
            "org.jetbrains.security.package-checker",
            "org.toml.lang",
            "tanvd.grazi",
            "training",
        )
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}


