plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    id("org.jetbrains.intellij")
}

version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.7.0")
    testImplementation("org.powermock:powermock-api-mockito2:2.0.9")
    testImplementation("org.powermock:powermock-core:2.0.9")
}

kotlin {
    jvmToolchain(11)
}

intellij {
    version.set("2022.1.4")
}

tasks {
    test {
        doFirst {
            classpath.files.forEach {
                println("test-classpath-project-resources: Test classpath entry: ${it.absolutePath}")
            }
        }
    }
}
