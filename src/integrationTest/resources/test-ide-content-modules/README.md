# Test IDE Content Modules

## Intention

The intention is to test that bundled and JetBrains Marketplace plugins are loaded in tests when they depend on content
modules or plugins provided by other bundled plugins, which are added to the test classpath with all bundled plugins
declared in `product-info.json`.

The IDE loads a content module only together with the plugin declaring it in `<content>`.
In IntelliJ Platform 2026.2, `com.intellij.java` requires the `intellij.platform.structureView` content module declared by
the `intellij.structureView.plugin` plugin, and `module intellij.platform.structureView is not resolved` was logged
when the latter was missing, as reported in:

- https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2158

In IntelliJ Platform 2024.3, the Go plugin from JetBrains Marketplace requires the bundled JSON plugin, and its classes
were unavailable at runtime when the latter was missing, as reported in:

- https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1798

## Approach

The test project declares bundled plugins and JetBrains Marketplace plugins passed with the `bundledPlugins`
and `marketplacePlugins` project properties.
The project test asserts that the plugins passed with the `expectedPlugins` project property are loaded.

## Verification

1. The project tests pass with the `test` and custom `testIde` tasks, so the expected plugins are loaded by the IDE.
2. The test classpath contains the plugins declaring the required content modules.
