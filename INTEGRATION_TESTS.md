# IntelliJ Platform Gradle Plugin Integration Tests

## Purpose of running Integration Tests

The IntelliJ Platform Gradle Plugin already contains a set of unit tests, but in some cases, it is not enough when a
more complex configuration is used.

Integration Tests are supposed to build the IntelliJ Platform Gradle Plugin from the sources held in the currently used
branch (i.e., for specific commits, pull request) and use it against the curated list of subprojects.

Each of the Integration Tests is a standalone project that focuses on a specific use-case, like patching
the `plugin.xml` file, running one of the small IDEs with extra dependencies and feature implemented, etc.

## How it works

Running Integration Tests is based on GitHub Actions and makes use of the matrix build so that we can run all tests with
variations of different properties.

See [GitHub Workflows README](.github/workflows/README.md).

Each matrix variation is used to run the Integration Tests projects as a separate step.
Its Gradle build output (if succeeded) is used for further verification with the dedicated
`verify.main.kts` script using assertions.

### GitHub Actions Workflow

The Integration Tests workflow is stored in the [`integration-tests.yml`](../.github/workflows/integration-tests.yml)
file.
It defines a couple of triggers:

- `workflow_dispatch` – manual trigger via the [Actions](https://github.com/JetBrains/intellij-platform-gradle-plugin/actions)
  tab of the IntelliJ Platform Gradle Plugin GitHub repository.
- `schedule` (WIP) – CRON job
- `push` (WIP) – trigger any push to the main branch
- `pull_request` (WIP) – trigger on any push to pull requests

The very first job is `Collect Modules` which, using the
dedicated [`list_integration_test_modules.main.kts`](../.github/scripts/list_integration_test_modules.main.kts) Kotlin
Script that dynamically lists available modules stored in the [`integration-tests`](../integration-tests) directory.

Each variation executes the `verify.main.kts` script which should execute a Gradle task required for the test.
The Gradle task execution is performed via the `runGradleTask()` function, which returns build log that can be
used for verification of the build correctness.

### Integration Tests structure

```
.
├── ...
├── integration-tests
│   ├── README.md                 this document
│   ├── build.gradle.kts          introduces `integrationTest` task
│   ├── [subproject name]
│   │   ├── build                 output build directory
│   │   ├── build.gradle.kts      custom project configuration
│   │   ├── src                   module sources, like Java/Kotlin implementation, plugin.xml, other resources
│   │   └── verify.main.kts       custom verification script containing assertions against build output, artifact, and Gradle output
│   ├── settings.gradle.kts       combines subprojects, loads IntelliJ Platform Gradle Plugin
│   └── verify.utils.kts          util methods for verify.main.kts scripts which are located in modules
└── ...
```

To introduce a new subproject to the Integration Tests set, it is required to create a new directory within
the `integration-tests` folder and provide at least `build.gradle.kts` and `verify.main.kts` scripts.

The `build.gradle.kts` should apply the IntelliJ Platform Gradle Plugin without specifying its version and define
dependencies of the `integrationTest` task:

```kotlin
plugins {
    id("org.jetbrains.intellij.platform")
}

// ...

tasks {
    integrationTest {
        dependsOn(patchPluginXml)
    }
}
```

The `verify.main.kts` file for assertions used to check the given module's output has to import a utility file with
shebang having assertions enabled:

```kotlin
#!/usr/bin/env kotlin -J-ea

@file:Import("../verify.utils.kts")

// ...
```

### Verification file

Each subproject must provide `verify.main.kts` that runs assertions against files/logs produced during the run.
The utility file provides common methods used for assertions or file handlers one may be interested in,
like: `workingDirPath`, `patchedPluginXml`, `buildDirectory`.

To verify the correctness of the output, you may check if it contains specific strings or matches regular expressions:

```kotlin
logs matchesRegex ":plugin-xml-patching:patchPluginXml .*? completed."

patchedPluginXml containsText "<idea-version since-build=\"2021.1\" until-build=\"2021.3.*\" />"
```

## Running Integration Tests locally

To invoke the `verify.main.kts` script, navigate to the file and click the green arrow on the first script line.

> **Important:** Because of [KT-42101](https://youtrack.jetbrains.com/issue/KT-42101), Kotlin Script doesn't invalidate
> changes of the imported `verify.utils.kts` file.
> If you modify scripts loaded using `@file:Import`, make sure to update the content of the `verify.main.kts` file to
> invalidate cache, or set the `KOTLIN_MAIN_KTS_COMPILED_SCRIPTS_CACHE_DIR` environment variable to an empty value,
> like:
> ```Bash
> KOTLIN_MAIN_KTS_COMPILED_SCRIPTS_CACHE_DIR= /Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/instrumentation-task/verify.main.kts
> ```

Alternatively, set this environment variable in
[Preferences | Tools | Terminal](jetbrains://idea/settings?name=Tools--Terminal) to make it available in the IntelliJ
IDEA Terminal.

# Integration Tests List

| Name                                   | Description                                                                                  |
|----------------------------------------|----------------------------------------------------------------------------------------------|
| attaching-plugin-bundled-sources       | Verifies if plugin bundled source JARs are attached in the Ivy file as source artefacts.     |
| attaching-plugin-sources-from-ide-dist | Verifies if sources provided in the IDE distribution are attached to plugin dependency.      |
| build-features                         | Tests enabling/disabling build features. See: `org.jetbrains.intellij.BuildFeature`.         |
| classpath                              | Verifies if custom dependency is added to configurations classpath in proper order.          |
| instrumentation-task                   | Process only Java and Swing form files during the code instrumentation.                      |
| instrumentation-task-disabled          | Check if plugin is correctly assembled with the instrumentation task manually disabled.      |
| jar-manifest-file                      | Verifies the `MANIFEST.MF` file generated and bundled within the produced JAR.               |
| plugin-xml-patching                    | Verifies if the content of the generated `plugin.xml` file has properties correctly updated. |
| throwing-exceptions                    | Forces plugin to throw exceptions due to misconfigurations or other unexpected behaviours.   |
| verify-plugin-configuration            | Forces plugin to show warnings about improper Java/Kotlin configurations.                    |
