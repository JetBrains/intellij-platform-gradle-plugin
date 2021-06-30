# Plugin with dependencies

The plugin adds two actions: `Say Hello to Coverage` and `Say Hello to Kotlin` in `Tools` menu and to Welcome Screen.

The first action requires built-in `coverage` plugin for compilation, the second one requires external `kotlin` plugin for IntelliJ.

- Both compile dependencies are satisfied by `gradle-intellij-plugin`
- Both plugins-dependencies will be install in debug IntelliJ instance in runtime

The actions will be added to IDE only if corresponding dependency-plugin is enabled in debug IntelliJ instance.

## Usage

`./gradlew :plugin-with-dependencies:runIde`
