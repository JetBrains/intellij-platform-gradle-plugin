## Configuration

Plugin provides following options to configure target IntelliJ SDK and build archive

### intellij

| **Attribute**             | **Information** | **Values** |
| :-----------------------: | :-------------- | :--------- |
| `version`                 | The version of the IDEA distribution that should be used as a dependency. <br/><br/>**Notes:** `intellij.version` and `intellij.localPath` should not be specified at the same time. | **Acceptable Values:** <br/><ul><li><kbd>build #</kbd> - e.g. `example` </li><li><kbd>version #</kbd> - e.g. `example` </li><li><kbd>LATEST-EAP-SNAPSHOT</kbd></li><li><kbd>LATEST_TRUNK-SNAPSHOT</kbd></li></ul>**Default Value:** <kbd>LATEST-EAP-SNAPSHOT</kbd> |
| `localPath`               | The path to locally installed IDEA distribution that should be used as a dependency. <br/><br/>**Notes:** `intellij.version` and `intellij.localPath` should not be specified at the same time. | **Acceptable Values:** <br/><kbd>path</kbd> - e.g. `/Applications/IntelliJIDEA.app`</br>**Default Value:** <kbd>null</kbd> |
| `type`                    | The type of IDEA distribution. <br/><br/>**Notes:** notes. | **Acceptable Values:** <br/><ul><li><kbd>IC</kbd> - Community Edition. </li><li><kbd>IU</kbd> - Ultimate Edition. </li><li><kbd>JPS</kbd> - JPS-only. </li><li><kbd>RD</kbd> - Rider.</li></ul>**Default Value:** <kbd>IC</kbd> |
| `plugins`                 |  |
| `pluginName`              | The name of the target zip-archive and defines the name of plugin artifact. | **Acceptable Values:** <br/>any `String` value <br/><br/>**Default Value:** <kbd>$project.name</kbd> |
| `sandboxDirectory`        | The path of sandbox directory that is used for running IDEA with developing plugin. | **Acceptable Values:** <br/><kbd>path</kbd> - e.g. `/build/sandbox` <br/><br/>**Default Value:** <kbd>$project.buildDir/idea-sandbox</kbd> |
| `instrumentCode`          | Should plugin instrument java classes with nullability assertions? <br/><br/>**Notes:** Instrumentation code cannot be performed while using Rider distributions `RD`. <br/> Might be required for compiling forms created by IntelliJ GUI designer. | **Acceptable Values:** <br/><kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd> |
| `updateSinceUntilBuild`   | Should plugin patch `plugin.xml` with since and until build values? <br/><br/>**Notes:** If `true` then user-defined values from `patchPluginXml.sinceBuild` and `patchPluginXml.untilBuild` will be used (or their default values if none set). | **Acceptable Values:** <br/><kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd> |
| `sameSinceUntilBuild`     | Should plugin patch `plugin.xml` with an until build value that is just an "open" since build?  <br/><br/>**Notes:** Is useful for building plugins against EAP IDEA builds. <br/> If `true` then the user-defined value from `patchPluginXml.sinceBuild` (or its default value) will be used as a `since` and `until` value ("open"). <br/> If `patchPluginXml.untilBuild` has a value set, then `sameSinceUntilBuild` is ignored.  |  **Acceptable Values:** <br/><kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>false</kbd> |
| `downloadSources`         | Should plugin download IntelliJ sources while initializing Gradle build? <br/><br/>**Notes:** Since sources are not needed while testing on CI, you can set it to `false` for a particular environment. | **Acceptable Values:** <br/><kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd> if `CI` environment variable is not set |
| `alternativeIdePath`      | The absolute path to the locally installed JetBrains IDE. <br/><br/>**Notes:** Use this property if you want to test your plugin in any non-IDEA JetBrains IDE such as WebStorm or Android Studio. <br/> Empty value means that the IDE that was used for compiling will be used for running/debugging as well. | **Acceptable Values:** <br/><kbd>path</kbd> - e.g. `/Applications/Android Studio.app`<br/><br/>**Default Value:** none | 
| `ideaDependencyCachePath` | The absolute path to the local directory that should be used for storing IDEA distributions.. <br/><br/>**Notes:** Empty value means the Gradle cache directory will be used. | **Acceptable Values:** <br/><kbd>path</kbd> - e.g. `example`</br>**Default Value:** none | 
| `systemProperties`        | Description. <br/><br/>**Notes:** notes. | **Acceptable Values:** <br/><kbd>item1</kbd> <kbd>item2</kbd><br/><br/>**Default Value:** <kbd>value</kbd> |


<!-- 

| `type` | Description. <br/><br/>**Notes:** notes. | **Acceptable Values:** <br/><kbd>item1</kbd> <kbd>item2</kbd><br/><br/>**Default Value:** <kbd>value</kbd> |

<ul><li><kbd>item1</kbd> item1Description.</li><li><kbd>item2</kbd> item2Description.</li></ul>

-->


- `intellij.plugins` defines the list of bundled IDEA plugins and plugins from [idea repository](https://plugins.jetbrains.com/) 
that should be used as dependencies in format `org.plugin.id:version[@channel]`.
E.g. `plugins = ['org.intellij.plugins.markdown:8.5.0.20160208', 'org.intellij.scala:2017.2.638@nightly']`.
For bundled plugins a plugin's directory should be used as a name and a version should be omitted, e.g. `plugins = ['android', 'Groovy']`.
You can can also specify a Gradle subproject as a plugin dependency, e.g. `plugins = [project(':plugin-subproject')]`.<br/><br/>
**Default value:** `<empty>`

- `intellij.systemProperties` defines the map of system properties which will be passed to IDEA instance on
executing `runIdea` task and tests.<br/>
Also you can use `intellij.systemProperty(name, value)` method in order to set single system property.<br/><br/>
**Deprecated**. Use `systemProperties` methods of a particular tasks like `runIde` or `test`.<br/><br/>
**Default value**: `[]`


### Patching plugin.xml

The `patchPluginXml` task supports following properties:

- `version` is a value for `<version>` tag.<br/>
**Default value**: `<project.version>`

- `sinceBuild` is a value for `<idea-version since-build="">` attribute.<br/>
**Default value**: `<IntelliJIDEABuildNumber>`

- `untilBuild` is a value for `<idea-version until-build="">` attribute.<br/>
**Default value**: `<IntelliJIDEABranch.*>`

- `pluginDescription` is a value for `<description>` tag.<br/>
**Default value**: null

- `pluginXmlFiles` is a collections of xml files to patch.<br/>
**Default value**: `<all plugin.xml files with idea-plugin root tag in resources>`

- `destinationDir` is a directory to store patched xml files.<br/>
**Default value**: `<project.buildDir>/patchedPluginXmlFiles`

### Publishing plugin

**`intellij.publish.\* properties are deprecated**
- `intellij.publish.username` your login at JetBrains plugin repository.
- `intellij.publish.password` your password at JetBrains plugin repository.
- `intellij.publish.channel` defines channel to upload, you may use any string here, empty string means default channel.
- `intellij.publish.channels` defines several channels to upload, you may use any comma-separated strings here, 
`default` string means default channel.<br/><br/>
**Default value**: `<empty>`

`publishPlugin` task supports following properties:

- `username` is a login at JetBrains plugin repository.
- `password` is a password at JetBrains plugin repository.
- `channels` are channels names to upload the plugin to.<br/>
**Default value**: `[default]`

- `host` host of plugin repository.<br/>
**Default value**: `http://plugins.jetbrains.com`

- `distributionFile` is a file to upload.<br/>
**Default value**: `<output of buildPlugin task>`


