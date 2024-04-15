val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")
val instrumentCodeProperty = project.property("instrumentCode") == "true"

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
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = instrumentCodeProperty
}

tasks {
    test {
        useJUnit()

        testLogging {
            outputs.upToDateWhen { false }
            showStandardStreams = true
        }
    }
}
