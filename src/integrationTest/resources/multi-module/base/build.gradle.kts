// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.apache.xmlgraphics:batik-all:1.17") {
        exclude("xml-apis")
        exclude("commons-io")
        exclude("commons-logging")
    }
    implementation("xml-apis:xml-apis-ext:1.3.04")

    intellijPlatform {
        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
        bundledPlugins("com.intellij.java")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}
