plugins {
    id("java")
    id("kotlin")
    id("org.jetbrains.intellij")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
}

intellij {
    version.set("2022.1.4")
}
