plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}
