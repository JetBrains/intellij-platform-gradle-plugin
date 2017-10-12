### Tasks

Plugin introduces the following tasks

| **Task** | **Description** |
| -------- | --------------- |
| `patchPluginXml` | Collects all plugin.xml files in sources and fill since/until build and version attributes. |
| `prepareSandbox` | Creates proper structure of plugin, copies patched plugin xml files and fills sandbox directory with all of it. |
| `buildPlugin`    | Assembles plugin and prepares zip archive for deployment. |
| `runIde`         | Executes an IntelliJ IDEA instance with the plugin you are developing. |
| `publishPlugin`  | Uploads plugin distribution archive to http://plugins.jetbrains.com. |

**Available in SNAPSHOT:**

| **Task** | **Description** |
| -------- | --------------- |
| `verifyPlugin` | Validates plugin.xml and plugin's structure. |
