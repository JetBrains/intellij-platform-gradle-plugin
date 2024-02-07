// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

version = "1.0.0"

repositories {
    mavenCentral()

    intellijPlatform {
        releases()
    }
}

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("intellijPlatform.type"), providers.gradleProperty("intellijPlatform.version"))
    }

    implementation(project(":searchable-options-submodule"))
}

intellijPlatform {
    instrumentCode = false
}

kotlin {
    jvmToolchain(17)
}

//tasks {
//    patchPluginXml {
//        sinceBuild.set("211")
//        untilBuild.set("213.*")
//    }
//}
