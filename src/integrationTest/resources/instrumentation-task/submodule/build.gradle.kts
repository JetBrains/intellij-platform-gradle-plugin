plugins {
    id("java")
    id("kotlin")
    id("org.jetbrains.intellij")
}

repositories {
    mavenCentral()
}

intellij {
    version.set("2022.1.4")
}
