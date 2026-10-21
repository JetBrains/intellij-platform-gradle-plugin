# Test IDE Content Modules

## Intention

The intention is to test that bundled plugins and modules are loaded in tests when they depend on content modules
declared by other bundled plugins, which aren't added to the test classpath otherwise.

The IDE loads a content module only together with the plugin declaring it in `<content>`.
In IntelliJ Platform 2026.2, `com.intellij.java` requires the `intellij.platform.structureView` content module declared by
the `intellij.structureView.plugin` plugin.
With `org.jetbrains.intellij.platform.testIdeBundledPluginsClasspathEnabled=false`, `com.intellij.java` was excluded,
and `module intellij.platform.structureView is not resolved` was logged, as reported in:

- https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2158

## Approach

The test project declares bundled plugins and modules passed with the `bundledPlugins` and `bundledModules` project properties.
The project test asserts that the plugins passed with the `expectedPlugins` project property are loaded.

## Verification

1. The project test passes, so the expected plugins are loaded by the IDE.
2. The test classpath contains the plugins declaring the required content modules.
