import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.tasks.PatchPluginXmlTask

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.22" apply false
    // Fix for "Multiple incompatible variants of org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.7.22 were selected". Should be fixed in 1.8.20
    // See: https://youtrack.jetbrains.com/issue/KT-54691/Kotlin-Gradle-Plugin-libraries-alignment-platform
    id("org.jetbrains.kotlin.plugin.sam.with.receiver") version "1.7.22"
    id("org.jetbrains.intellij") version "0.0.0" apply false
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

tasks {
    wrapper {
        jarFile = file("../gradle/wrapper/gradle-wrapper.jar")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.intellij")

    repositories {
        mavenCentral()
    }

    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    }

    configure<IntelliJPluginExtension> {
        type.set(properties("platformType"))
        version.set(properties("platformVersion"))
    }

    tasks.named<PatchPluginXmlTask>("patchPluginXml") {
        version.set(properties("version"))
    }
}
