### Tasks

Plugin introduces the following tasks

| **Task** | **Description** |
| -------- | --------------- |
| `buildPlugin`            | Assembles plugin and prepares ZIP archive for deployment. |
| `patchPluginXml`         | Collects all plugin.xml files in sources and fill since/until build and version attributes. |
| `downloadRobotServerPlugin` | Downloads robot-server plugin which is needed for ui tests running. | 
| `prepareSandbox`         | Creates proper structure of plugin, copies patched plugin xml files and fills sandbox directory with all of it. |
| `prepareTestingSandbox`  | Prepares sandbox that will be used while running tests. |
| `prepareUiTestingSandbox` | Prepares sandbox that will be used while running ui tests. |
| `buildSearchableOptions` | Builds an index of UI components (a.k.a. searchable options) for the plugin by running a headless IDE instance.<br>Note, that this is a `runIde` task with predefined arguments and all properties of `runIde` task are also applied to `buildSearchableOptions` tasks. |
| `jarSearchableOptions`   | Creates a jar file with searchable options to be distributed with the plugin. |
| `runIde`                 | Executes an IntelliJ IDEA instance with the plugin you are developing. |
| `runIdeForUiTests`       | Executes an IntelliJ IDEA instance ready for ui tests run with the plugin you are developing. See [intellij-ui-test-robot](https://github.com/JetBrains/intellij-ui-test-robot) project to know more |
| `publishPlugin`          | Uploads plugin distribution archive to https://plugins.jetbrains.com. |
| `runPluginVerifier`      | Runs the [IntelliJ Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) tool to check the binary compatibility with specified IntelliJ IDE builds. |
| `verifyPlugin`           | Validates completeness and contents of plugin.xml descriptors as well as plugin’s archive structure. |
| `signPlugin`             | Signs the ZIP archive with the provided key using [marketplace-zip-signer](https://github.com/JetBrains/marketplace-zip-signer) library. |
