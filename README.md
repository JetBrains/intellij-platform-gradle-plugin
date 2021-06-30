# Gradle IntelliJ Plugin Examples

This repository contains a set of example modules using the Gradle IntelliJ Plugin:

- clion-plugin
- composite-plugin
- composite-plugin:subplugin
- custom-runide-task
- kotlin-plugin
- plugin-with-dependencies
- pycharm-plugin
- rider-plugin
- simple-plugin

These modules are configured to point to the actual Gradle IntelliJ Plugin's sources cloned in a sibling directory.
In the `settings.gradle.kts` file, you may find:

```kotlin
includeBuild("../gradle-intellij-plugin")
```

Clone the `https://github.com/JetBrains/gradle-intellij-plugin` repository twice:
- as `gradle-intellij-plugin` – here we'll have the current working branch: `master`, feature-branch, etc.
- as `gradle-intellij-plugin-examples` – checkout the `examples` branch here and stick to it

The main project should be used as a development environment.
Avoid switching to the `examples` branch due to circular dependencies issues.

`gradle-intellij-plugin-examples` project is supposed to be used for manual integration testing.
