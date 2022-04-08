import java.nio.file.Files

pluginManagement {
    repositories {
        mavenLocal()
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.jetbrains.intellij") {
                useModule("org.jetbrains.intellij.plugins:gradle-intellij-plugin:${requested.version}")
            }
        }
    }
}

rootProject.name = "integration-tests"

Files.list(rootDir.toPath())
    .filter { Files.isDirectory(it) }
    .map { it.fileName.toString() }
    .filter { !it.startsWith(".") }
    .forEach {
        include(it)
    }
