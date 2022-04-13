plugins {
    id("org.jetbrains.intellij")
}

repositories {
    mavenCentral()
}

intellij {
    version.set("2022.1")
}

tasks {
    patchPluginXml {
        version.set("1.0.0")
        sinceBuild.set("221")
        untilBuild.set("221.*")
    }

    integrationTest {
        dependsOn(patchPluginXml)
    }
}
