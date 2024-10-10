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

buildscript {
    // https://docs.gradle.org/current/userguide/dependency_locking.html
    dependencyLocking {
        lockAllConfigurations()
        lockFile = file("gradle/locks/root/gradle-buildscript.lockfile")
        lockMode.set(LockMode.DEFAULT)
        //ignoredDependencies.add("bundledModule:*")
        //ignoredDependencies.add("bundledPlugin:*")
    }
}
// https://docs.gradle.org/current/userguide/dependency_locking.html
dependencyLocking {
    lockAllConfigurations()
    // There seems to be no way to customize the location of settings-gradle.lockfile
    lockFile = file("gradle/locks/root/gradle.lockfile")
    lockMode.set(LockMode.DEFAULT)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)

        // This is important for bug reproduction because we need some dependencies in the test
        // Use ./gradlew printBundledPlugins
        bundledPlugins(
            "AntSupport",
            "ByteCodeViewer",
            "Coverage",
            "DevKit",
            "Git4Idea",
            "JUnit",
            "Lombook Plugin",
            "PerforceDirectPlugin",
            "Subversion",
            "TestNG-J",
            "XPathView",
            "XSLT-Debugger",
            "com.android.tools.design",
            "com.android.tools.idea.smali",
            "com.intellij.completion.ml.ranking",
            "com.intellij.configurationScript",
            "com.intellij.copyright",
            "com.intellij.dev",
            "com.intellij.gradle",
            "com.intellij.java",
            "com.intellij.java-i18n",
            "com.intellij.java.ide",
            "com.intellij.marketplace",
            "com.intellij.platform.images",
            "com.intellij.plugins.eclipsekeymap",
            "com.intellij.plugins.netbeanskeymap",
            "com.intellij.plugins.visualstudiokeymap",
            "com.intellij.properties",
            "com.intellij.searcheverywhere.ml",
            "com.intellij.settingsSync",
            "com.intellij.tasks",
            "com.intellij.tracing.ide",
            "com.intellij.uiDesigner",
            "com.jetbrains.codeWithMe",
            "com.jetbrains.packagesearch.intellij-plugin",
            "com.jetbrains.projector.libs",
            "com.jetbrains.sh",
            "com.jetbrains.space",
            "hg4idea",
            "intellij.indexing.shared.core",
            "intellij.webp",
            "org.editorconfig.editorconfigjetbrains",
            "org.intellij.groovy",
            "org.intellij.intelliLang",
            "org.intellij.plugins.markdown",
            "org.intellij.qodana",
            "org.jetbrains.android",
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
            "org.jetbrains.plugins.gradle",
            "org.jetbrains.plugins.gradle.dependency.updater",
            "org.jetbrains.plugins.gradle.maven",
            "org.jetbrains.plugins.javaFX",
            "org.jetbrains.plugins.terminal",
            "org.jetbrains.plugins.textmate",
            "org.jetbrains.plugins.yaml",
            "org.toml.lang",
            "tanvd.grazi",
            "training"
        )
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}


