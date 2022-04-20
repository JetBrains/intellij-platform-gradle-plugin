# Gradle IntelliJ Plugin Integration Tests

## Purpose of running Integration Tests

The Gradle IntelliJ Plugin already contains a set of unit tests, but in some cases, it is not enough when a more complex configuration is used.

Integration Tests are supposed to build the Gradle IntelliJ Plugin from the sources held in the currently used branch (i.e., for specific commits, pull request) and use it against the curated list of subprojects.

Each of the Integration Tests is a standalone project that focuses on a specific use-case, like patching the `plugin.xml` file, running one of the small IDEs with extra dependencies and feature implemented, etc.

## How it works

Running Integration Tests is based on GitHub Actions and makes use of the matrix build so that we can run all tests with variations of different properties:
- Operating systems: macOS, Windows, Linux
- Gradle versions:
  - first supported: `6.7`
  - last from `6.x` branch: `6.9.2`
  - last available: `7.4.2`
- IntelliJ IDEA SDK versions:
  - latest stable: `2021.3.3`

Each matrix variation is used to run the Integration Tests projects as a separate step. Its Gradle build output (if succeeded) is used for further verification with the dedicated `verify.main.kts` script using assertions.

### GitHub Actions Workflow

The Integration Tests workflow is stored in the [`integration-tests.yml`](../.github/workflows/integration-tests.yml) file.
It defines a couple of triggers:
- `workflow_dispatch` – manual trigger via the [Actions](https://github.com/JetBrains/gradle-intellij-plugin/actions) tab of the Gradle IntelliJ Plugin GitHub repository.
- `schedule` (WIP) – CRON job
- `push` (WIP) – trigger any push to the main branch
- `pull_request` (WIP) – trigger on any push to pull requests

The very first job is `Collect Modules` which, using the dedicated [`list_integration_test_modules.main.kts`](../.github/scripts/list_integration_test_modules.main.kts) Kotlin Script that dynamically lists available modules stored in the [`integration-tests`](../integration-tests) directory.

Each variation triggers the Gradle `integrationTest` dedicated task that each subproject can configure – i.e., to make it dependent on other tasks.
The output of this run is stored in the module's `./build/integrationTestOutput.txt` file, which is further passed to the module's `verify.main.kts` script.

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
│   ├── settings.gradle.kts       combines subprojects, loads Gradle IntelliJ Plugin
│   └── verify.utils.kts          util methods for verify.main.kts scripts which are located in modules
└── ...
```

To introduce a new subproject to the Integration Tests set, it is required to create a new directory within the `integration-tests` folder and provide `build.gradle.kts`, `src`, `verify.main.kts`.

The `build.gradle.kts` should apply the Gradle IntelliJ Plugin without specifying its version and define dependencies of the `integrationTest` task:

```kotlin
plugins {
    id("org.jetbrains.intellij")
}

// ...

tasks {
  integrationTest {
    dependsOn(patchPluginXml)
  }
}
```

The `verify.main.kts` file for assertions used to check the given module's output has to import a utility file with shebang having assertions enabled:

```kotlin
#!/usr/bin/env kotlin -J-ea

@file:Import("../verify.utils.kts")

// ...
```

### Verification file

Each subproject must provide `verify.main.kts` that runs assertions against files/logs produced during the run.
The utility file provides common methods used for assertions or file handlers one may be interested in, like: `workingDirPath` ,`patchedPluginXml` ,`buildDirectory`.

To verify the correctness of the output, you may check if it contains specific strings or matches regular expressions:

```kotlin
logs matchesRegex ":plugin-xml-patching:patchPluginXml .*? completed."

patchedPluginXml containsText "<idea-version since-build=\"2021.1\" until-build=\"2021.3.*\" />"
```

## Running Integration Tests locally

To invoke the `verify.main.kts` script, navigate to the file and click the green arrow on the first script line.


# Integration Tests List

| Name                           | Description                                                                                |
|--------------------------------|--------------------------------------------------------------------------------------------|
| instrumentation-task-disabled  | Check if plugin is correctly assembled with the instrumentation task manually disabled.    |
| instrumentation-task-java-only | Process only Java and Swing form files during the code instrumentation.                    |
| plugin-xml-patching            | Verify if the content of the generated `plugin.xml` file has properties correctly updated. |
