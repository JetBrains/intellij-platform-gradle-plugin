plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.20"
    id("org.jetbrains.intellij") version "1.5.2" apply false
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
    tasks.create("integrationTest")
}

val TaskContainer.integrationTest: TaskProvider<Task>
    get() = named<Task>("integrationTest")
