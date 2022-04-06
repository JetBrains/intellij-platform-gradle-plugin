import java.nio.file.Files
import kotlin.streams.toList

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
    }
}

rootProject.name = "integration-tests"

includeBuild("..")

val submodules = Files.list(rootDir.toPath())
    .filter { Files.isDirectory(it) }
    .map { it.fileName.toString() }
    .filter { !it.startsWith(".") }
    .toList()

include(submodules)
