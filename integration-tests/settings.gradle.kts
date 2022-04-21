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

// Include current soources if building project locally.
// On CI, `mavenLocal` is used instead for the sake of performance.
if (!System.getenv().containsKey("CI")) {
    includeBuild("..")
}

Files.list(rootDir.toPath())
    .filter { Files.isDirectory(it) }
    .map { it.fileName.toString() }
    .filter { !it.startsWith(".") }
    .forEach {
        include(it)
    }
