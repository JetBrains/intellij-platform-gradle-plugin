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
        sinceBuild.set("211")
        untilBuild.set("213.*")
    }

    integrationTest {
        dependsOn(clean, patchPluginXml)
    }
}
