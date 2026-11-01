# Test IDE Regressions

## Intention

This project is shared by `TestIdeRegressionsIntegrationTest` and `TestIdeBundledPluginsClasspathIntegrationTest`.
The intention is to test that running tests still works for the setups reported in issues about running tests and the
test classpath, now that the `test` task classpath contains all bundled plugins declared in `product-info.json`, on the
IntelliJ Platform versions from the oldest supported 2023.3 (build 233) to the 2026.3 EAP, including:

- IntelliJ Platform with no `layout` in `product-info.json` (2023.3–2024.1)
- IntelliJ Platform resolved from the IntelliJ Maven Repository (`useInstaller = false`)
- products other than IntelliJ IDEA: CLion, PyCharm
- custom `testIde` tasks targeting another IntelliJ Platform:
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1864,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1945,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1993,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2015,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2022,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2074
- bundled plugins and their services available without declaring them:
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1710,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1798,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2250,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2273
- bundled plugins failing in tests, which are excluded by default or disabled explicitly:
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1844,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2182,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2183,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2185,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2261
- optional dependencies of the tested plugin on excluded or disabled bundled plugins:
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/115,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1887
- test framework and libraries:
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1663,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1711,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1815,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1997,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2045,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2127,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2137
- duplicated classpath entries and resources:
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/562,
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1640
- the build output and the configuration cache:
  https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2119

## Approach

The test project uses the IntelliJ Platform passed with the `intellijPlatform.type`, `intellijPlatform.version`, and
`useInstaller` project properties, the test frameworks passed with the `testFrameworks` project property, and declares
bundled plugins passed with the `bundledPlugins` and `testBundledPlugins` project properties.
The `customTestIde.type` and `customTestIde.version` project properties register the `testIdeCustom` task.
The tested plugin has an optional dependency on the bundled YAML plugin and an internal file template.

The `testClasses` project property selects the project tests to run:

- `SmokeTest` asserts the `idea.home.path` system property, the IDE build, that the tested plugin and the plugins passed with the
  `expectedPlugins` project property are loaded, the plugins passed with `unexpectedPlugins` aren't loaded, and that the file
  types passed with `expectedFileTypes` and the services passed with `expectedServices` are available,
- `HeavySmokeTest` opens a project, which runs startup activities of all loaded plugins,
- `FileComparisonTest` asserts that a failed file comparison throws the file comparison error; with
  `fileComparisonDataExpected=true`, it also checks the exact error class and the `FileComparisonData` interface,
- `FileTemplateTest` asserts that the internal file template of the tested plugin is found once,
- `OptionalDependencyTest` asserts that the optional YAML dependency is loaded unless the `yamlExpected` project property is `false`,
- `JnaTest` asserts that JNA is loaded,
- `JUnit5SmokeTest` and `JavaFixtureTest`, added by the integration tests using JUnit 5 and the Java test framework,
- `GitVcsTest`, added by the bundled-plugin classpath tests declaring `Git4Idea`, asserts that the Git VCS is registered.

## Verification

1. The project tests pass, with no errors logged.
2. The test classpath contains no duplicated entries.
3. The build output contains the expected messages.
