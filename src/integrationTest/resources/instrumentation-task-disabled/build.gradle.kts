val instrumentCodeProperty = project.property("instrumentCode") == "true"

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    id("org.jetbrains.intellij")
}

version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
}

intellij {
    version.set("2022.1.4")
    instrumentCode.set(instrumentCodeProperty)
}

tasks {
    test {
        useJUnit()

        testLogging {
            outputs.upToDateWhen { false }
            showStandardStreams = true
        }
    }

    buildSearchableOptions {
        enabled = false
    }
}
