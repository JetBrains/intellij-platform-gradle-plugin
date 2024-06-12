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
    }

    implementation(project(":submodule"))
}

sourceSets {
    main {
        java.srcDirs("customSrc")
    }
}

intellijPlatform {
    buildSearchableOptions = false
}
