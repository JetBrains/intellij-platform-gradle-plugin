# Contributing to the IntelliJ Platform Gradle Plugin

There are many ways to contribute to the IntelliJ Platform Gradle Plugin project, and each of them is valuable to us.
Every submitted feedback, issue, or pull request is highly appreciated.

## Issue Tracker

Before reporting an issue, please update your configuration to use always
the [latest release](https://github.com/JetBrains/intellij-platform-gradle-plugin/releases), or try with
the [snapshot release](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#snapshot-release), which contains not-yet publicly
available changes.

If you find your problem unique, and it wasn't yet reported to us, [file an issue](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/new)
using the provided issue template.

## Integration Tests

The project provides Unit Tests and Integration Tests to verify if nothing is broken with the real-life project examples.
[Integration Tests](https://github.com/JetBrains/intellij-platform-gradle-plugin/tree/main/src/integrationTest) provide various different test cases with
dedicated verification scenarios available in test class and associated project directory located in Integration Tests resources.
Read the [IntelliJ Platform Gradle Plugin Integration Tests](INTEGRATION_TESTS.md) document to find more about this kind of tests and find out how to create new
scenarios.

## Link With Your Project

It is possible to link the IntelliJ Platform Gradle Plugin project with your plugin project, so it'll be loaded and built as a module.
To integrate it with another consumer-like project, add the following line in the `settings.gradle.kts` Gradle settings file:

```kotlin
includeBuild("/path/to/intellij-platform-gradle-plugin")
```

The Gradle project needs to be refreshed to apply changes.

> [!NOTE]  
> 
> If you use the IntelliJ Platform Gradle Plugin settings plugin by applying `org.jetbrains.intellij.platform.settings` in `settings.gradle.kts`, it is necessary also to add `includeBuild(...)` to the `pluginManagement` section:
> 
> ```kotlin
> pluginManagement {
>    includeBuild("/path/to/intellij-platform-gradle-plugin")
> }
> ```

## Pull Requests

To correctly prepare the pull requests, make sure to provide the following information:

- proper title and description of the GitHub Pull Request – describe what your change introduces, what issue it fixes, etc.
- relevant entry in the [`CHANGELOG.md`](https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/main/CHANGELOG.md) file
- unit tests (if necessary)
- integration tests (if necessary)
- documentation (if necessary, available in [JetBrains/intellij-sdk-docs](https://github.com/JetBrains/intellij-sdk-docs/tree/main/topics/appendix/tools/intellij_platform_gradle_plugin) repository)
