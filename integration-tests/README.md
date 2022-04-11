# Gradle IntelliJ Plugin Integration Tests

## Purpose of running Integration Tests

The Gradle IntelliJ Plugin already contains a set of unit tests, but in some cases, it is not enough when a more complex configuration is used.

Integration Tests are supposed to build the Gradle IntelliJ Plugin from the sources held in the currently used branch (i.e., for specific commits, pull request) and use it against the curated list of subprojects.

Each of the Integration Tests is a standalone project that focuses on the specific flow, like patching the `plugin.xml` file, running one of the small IDEs with extra dependencies and feature implemented, etc.

## How it works

Running Integration Tests is based on GitHub Actions and makes use of the matrix build so that we can run such tests within variations of different properties:
- operation systems: macOS, Windows, Linux
- Gradle versions:
  - first supported: `6.6.1`
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

Each variation triggers the Gradle `integrationTest` dedicated task that each module can configure – i.e., to make it dependent on other tasks.
The output of this run is stored in the module's `./build/integrationTestOutput.txt` file, which is further passed to the module's `verify.main.kts` script.

### Integration Tests structure

```
.
├── ...
├── integration-tests
│   ├── README.md                 this document
│   ├── build.gradle.kts          introduces `integrationTest` task
│   ├── [module name]
│   │   ├── build                 output build directory
│   │   ├── build.gradle.kts      custom project configuration
│   │   ├── src                   module sources, like Java/Kotlin implementation, plugin.xml, other resources
│   │   └── verify.main.kts       custom verification script containing assertions against build output, artifact, and Gradle output
│   ├── settings.gradle.kts       combines modules, loads Gradle IntelliJ Plugin
│   └── verify.utils.kts          util methods for verify.main.kts scripts which are located in modules
└── ...
```

To introduce a new module to the Integration Tests set, it is required to create a new directory within the `integration-tests` module and provide `build.gradle.kts`, `src`, `verify.main.kts`, similar way to other modules.

The `build.gradle.kts` should apply the Gradle IntelliJ Plugin without the version specified and define dependencies of the `integrationTest` task:
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

Each module should provide the `verify.main.kts` that run assertions against files/logs produced during the run.
The utility file provides common methods used for assertions or file handlers one may be interested in, like: `rootPath` ,`pluginXml` ,`buildOutput`.

To verify the correctness of the output, you may check if it contains specific strings or matches regular expressions:

```kotlin
buildOutput matchesRegex ":plugin-xml-patching:patchPluginXml .*? completed."

pluginXml containsText "<idea-version since-build=\"2021.1\" until-build=\"2021.3.*\" />"
```

## Running Integration Tests locally

Open the Gradle Tool Window, click the `+` button, and point to the `integration-tests` directory.

To perform a test of the specific module, navigate to `integration-tests > integration-tests (root) > my-module > Tasks > other > integrationTest` or use the `Run Anything...` action with `my-module:integrationTest` Gradle task directly.
Note, if you run Gradle tasks from CLI, your working directory should be `/integration-tests` to keep all dependencies working.

To invoke the `verify.main.kts` script, navigate to the file and click the green arrow on the first script line.
It'll fail as it requires the path to the logs file provided; however, it opens the Terminal Tool Window when you can re-run it with the required argument.
