# Contributing to the IntelliJ Platform Gradle Plugin

There are many ways to contribute to the IntelliJ Platform Gradle Plugin project, and each of them is valuable to us.
Every submitted feedback, issue, or pull request is highly appreciated.

## Issue Tracker

Before reporting an issue, please update your configuration to use always
the [latest release](https://github.com/JetBrains/intellij-platform-gradle-plugin/releases) or try with
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

> [!NOTE]
> 
> If you load the IntelliJ Platform Gradle Plugin through a composite build, having the dependency on the plugin declared in the `buildSrc/build.gradle.kts` file like:
> 
> ```kotlin
> dependencies {
>    implementation("org.jetbrains.intellij.platform:intellij-platform-gradle-plugin:...")
> }
> ```
> 
> it is required to add the following substitution rule to the `buildSrc/settings.gradle.kts` file:
> 
> ```kotlin
> includeBuild("/Users/hsz/Projects/JetBrains/intellij-platform-gradle-plugin") {
>   dependencySubstitution {
>     substitute(module("org.jetbrains.intellij.platform:intellij-platform-gradle-plugin"))
>       .using(project(":"))
>   }
> }
> ```

## Code Style

The project is written in Kotlin (production code and the Gradle Kotlin DSL build scripts) and follows the
**default IntelliJ IDEA Kotlin code style**. There is no custom formatter, linter, or ktlint/Spotless setup –
the conventions below are simply what the existing sources already use, and they are captured in the
[`.editorconfig`](.editorconfig) file in the repository root so that any EditorConfig-aware editor picks them up
automatically.

When contributing, please match the surrounding code. The most important conventions are:

- **Encoding and line endings** – files are UTF-8, use Unix (`LF`) line endings, and end with a single trailing
  newline. Do not leave trailing whitespace (Markdown files are the only exception, where trailing spaces may be
  used as hard line breaks).
- **Indentation** – four spaces, never tabs. This applies to both Kotlin sources and the `*.gradle.kts` scripts.
  YAML files (for example, GitHub Actions workflows) use two spaces.
- **Line length** – aim for roughly 120 characters as a soft guide, matching the IDE default right margin.
  Longer lines are acceptable when wrapping would hurt readability – for example, URLs, KDoc, string literals, or
  enum entries carrying coordinates.
- **Imports** – let the IDE manage them (*Optimize Imports*). Remove unused imports and keep the default import
  ordering. The project does not forbid wildcard (`*`) imports; both single-name and wildcard imports produced by
  the IDE default settings are present, so there is no need to expand or collapse them by hand.
- **Braces** – use the K&R style produced by the IDE: the opening brace stays on the same line as the declaration
  or statement, and `else`/`catch`/`finally` continue on the line of the preceding closing brace.
- **Trailing commas** – keep a trailing comma on the last element of multi-line argument, parameter, and value
  lists (for example, multi-line `enum` entries or `data class` constructors). Single-line lists have no trailing
  comma.
- **Naming** – `PascalCase` for types (classes, interfaces, objects, enums), `camelCase` for functions,
  properties, and parameters, and `UPPER_SNAKE_CASE` for `const val` and top-level/`object` constants.
- **Documentation** – public API is documented with KDoc (`/** … */`) using the standard tags where relevant
  (`@param`, `@return`, `@throws`, `@property`, `@receiver`, `@see`). Each declaration has a single KDoc block.
- **Copyright header** – every Kotlin source file starts with the JetBrains copyright header on the very first
  line, followed by a blank line and the `package` declaration:

  ```kotlin
  // Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
  ```

  The header is maintained by the shared *JetBrains Copyrights* profile stored under
  [`.idea/copyright`](.idea/copyright); IntelliJ IDEA fills in the end year automatically. New files created in the
  IDE receive it via **Code | Copyright | Update Copyright**.

### Applying the style in IntelliJ IDEA

1. Open the project in IntelliJ IDEA. EditorConfig support is bundled and enabled by default, so the
   [`.editorconfig`](.editorconfig) settings are applied automatically – no extra configuration is required.
2. Before committing, run **Code | Reformat Code** (<kbd>Ctrl/Cmd</kbd>+<kbd>Alt</kbd>+<kbd>L</kbd>) and
   **Code | Optimize Imports** (<kbd>Ctrl/Cmd</kbd>+<kbd>Alt</kbd>+<kbd>O</kbd>) on the code you changed.
3. Reformat only the lines you actually touched. Please avoid repository-wide reformatting so that reviews stay
   focused on the change itself.

## Pull Requests

To correctly prepare the pull requests, make sure to provide the following information:

- proper title and description of the GitHub Pull Request – describe what your change introduces, what issue it fixes, etc.
- relevant entry in the [`CHANGELOG.md`](https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/main/CHANGELOG.md) file
- unit tests (if necessary)
- integration tests (if necessary)
- documentation (if necessary, available in the [JetBrains/intellij-sdk-docs](https://github.com/JetBrains/intellij-sdk-docs/tree/main/topics/appendix/tools/intellij_platform_gradle_plugin) repository)
