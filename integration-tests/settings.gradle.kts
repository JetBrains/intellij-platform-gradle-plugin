import java.nio.file.Files

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
    }
}

rootProject.name = "integration-tests"

includeBuild("..")

Files.list(rootDir.toPath())
    .filter { Files.isDirectory(it) }
    .map { it.fileName.toString() }
    .filter { !it.startsWith(".") }
    .forEach {
        include(it)
    }
