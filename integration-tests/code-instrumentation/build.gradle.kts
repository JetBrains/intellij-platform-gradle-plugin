plugins {
    id("org.jetbrains.intellij")
}

repositories {
    mavenCentral()
}

intellij {
    version.set("2021.1.3")
}

tasks {
    patchPluginXml {
        version.set("1.0.0")
        sinceBuild.set("2021.1")
        untilBuild.set("2021.3.*")
    }

    instrumentCode {
        enabled = false
    }

    integrationTest {
        dependsOn(buildPlugin)
    }
}
