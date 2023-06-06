plugins {
    id("org.jetbrains.kotlin.jvm")
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
}

tasks {
    patchPluginXml {
        sinceBuild.set("211")
        untilBuild.set("213.*")
    }
}
