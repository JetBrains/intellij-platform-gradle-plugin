# Test IDE Bundled Plugins Classpath

## Intention

The intention is to test that the `test` task classpath contains all bundled plugins declared in `product-info.json`,
using only the JARs listed by its `layout`, so that the IDE loads them in tests, as reported in:

- https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2250 – `Git4Idea` disabled in 2026.1 by a JetBrains
  Client plugin JAR of the Remote Development plugin, which isn't listed in the `layout`
- https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2261 – `Cannot create extension (class=Z.Z.Z.Z.Z)`
  in 2026.2.3, as the obfuscated classes of the Ultimate plugin clash with the core classes
- `FileComparisonFailedError` requiring `idea_rt.jar`, which is provided by the Java plugin since 2026.3

The bundled plugins listed with the `org.jetbrains.intellij.platform.testIdeBundledPluginsClasspathExcludes` Gradle
property are excluded, together with the bundled plugins requiring them, unless declared or required by the tested plugin.

## Approach

The test project uses the IntelliJ Platform passed with the `intellijPlatform.type`, `intellijPlatform.version`, and
`useInstaller` project properties, and declares bundled plugins passed with the `bundledPlugins` and `testBundledPlugins`
project properties.
The `testClasses` project property selects the project tests to run:

- `LoadedPluginsTest` asserts that the tested plugin and the plugins passed with the `expectedPlugins` project property are loaded,
- `FileComparisonFailedErrorTest` asserts that a failed file comparison throws `FileComparisonFailedError`,
- `GitVcsTest`, added by the integration tests declaring `Git4Idea`, asserts that the Git VCS is registered.

## Verification

1. The project tests pass.
2. The build output contains the expected warnings and the test classpath entries.
