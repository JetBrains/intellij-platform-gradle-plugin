rootProject.name = "gradle-intellij-plugin-examples"

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
    }
}

include(
    "clion-plugin",
    "composite-plugin",
    "composite-plugin:subplugin",
    "custom-runide-task",
    "kotlin-plugin",
    "plugin-with-dependencies",
    "pycharm-plugin",
    "rider-plugin",
    "simple-plugin",
)

includeBuild("../gradle-intellij-plugin")

