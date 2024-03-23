# Contributing to the IntelliJ Platform Gradle Plugin

There are many ways to contribute to the IntelliJ Platform Gradle Plugin project, and each of them is valuable to us.
Every submitted feedback, issue, or pull request is highly appreciated.

## Issue Tracker
Before reporting an issue, please update your configuration to use always the [latest release](https://github.com/JetBrains/gradle-intellij-plugin/releases), or try with the [snapshot release](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html), which contains not-yet publicly available changes.

If you find your problem unique, and it wasn't yet reported to us, [file an issue](https://github.com/JetBrains/gradle-intellij-plugin/issues/new) using the provided issue template.

## Run Integration Tests
The project provides Unit Tests and Integration Tests to verify if nothing is broken with the real-life project examples.
[Integration Tests](https://github.com/JetBrains/gradle-intellij-plugin/tree/master/integration-tests) provide various different test cases with a dedicated verification scenarios available in `verify.main.kts` files.
Read the [IntelliJ Platform Gradle Plugin Integration Tests](INTEGRATION_TESTS.md) document to find more about this kind of test and find out how to create new scenarios.

## Link With Your Project
It is possible to link your plugin project with the IntelliJ Platform Gradle Plugin project, so it'll be loaded and built as a module.

To integrate it with another consumer-like project, you can add it as a local build:

1. Add the following line in the Gradle settings file, `settings.gradle.kts`, pointing to the local repository:

```kotlin
includeBuild("/path/to/gradle-intellij-plugin")
```

2. To ensure you're not pulling in the changes from the remote repository, comment the lines from `gradle/libs.versions.toml`

```toml
[versions]
...
# intelliJPlatform = "2.0.0-SNAPSHOT"
...
[plugins]
...
# intelliJPlatform = { id = "org.jetbrains.intellij.platform", version.ref = "intelliJPlatform" }
```

3. In `build.gradle.kts`, replace the plugin dependency with the local one:

```kotlin
// alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
id("org.jetbrains.intellij")
```
4. Clean and build the project:

```shell
./gradlew clean buildPlugin
```

## Pull Requests
To correctly prepare the pull requests, make sure to provide the following information:
- proper title and description of the GitHub Pull Request – describe what your change introduces, what issue it fixes, etc.
- relevant entry in the [`CHANGES.md`](https://github.com/JetBrains/gradle-intellij-plugin/blob/master/CHANGES.md) file
- unit tests (if necessary)
- integration tests (if necessary)
