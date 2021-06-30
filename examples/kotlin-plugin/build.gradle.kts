plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.20"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test-junit"))
}
