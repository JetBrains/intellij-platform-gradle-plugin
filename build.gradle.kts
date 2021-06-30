@file:Suppress("UnstableApiUsage")

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.5.20"
    id("org.jetbrains.intellij")
}

buildscript {
    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-structure")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
}

repositories {
    mavenCentral()
}

subprojects {
    repositories {
        mavenCentral()
    }

    apply(plugin = "org.jetbrains.intellij")

    intellij {
        version.set("2020.1")
    }
}



tasks {
    wrapper {
        gradleVersion = "7.1"
        distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-${gradleVersion}-all.zip"
    }
}
