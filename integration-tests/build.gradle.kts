import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.tasks.PatchPluginXmlTask

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.21"
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
    apply<IntelliJPlugin>()

    repositories {
        mavenCentral()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    }

    configure<IntelliJPluginExtension> {
        type.set(properties("platformType"))
        version.set(properties("platformVersion"))
    }

    tasks.named<PatchPluginXmlTask>("patchPluginXml") {
        version.set(properties("version"))
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(properties("pluginUntilBuild"))
    }
}
