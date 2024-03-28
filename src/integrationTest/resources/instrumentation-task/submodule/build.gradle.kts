import org.jetbrains.intellij.platform.gradle.extensions.TestFrameworkType

val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")

version = "1.0.0"

plugins {
    id("java")
    id("kotlin")
    id("org.jetbrains.intellij.platform") // TODO: .submodule
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
        testFramework(TestFrameworkType.Platform.JUnit4)
    }
}
