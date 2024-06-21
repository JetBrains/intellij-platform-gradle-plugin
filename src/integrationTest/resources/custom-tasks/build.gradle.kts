package `custom-tasks`

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
        create(providers.gradleProperty("intellijPlatform.type"), providers.gradleProperty("intellijPlatform.version"))
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}

val runPhpStorm by intellijPlatform.createRunIdeTask()
