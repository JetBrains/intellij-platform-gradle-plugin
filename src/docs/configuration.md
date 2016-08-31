## Configuration

Plugin provides following options to configure target IntelliJ SDK and build archive

- `intellij.version` defines the version of IDEA distribution that should be used as a dependency. 
The option accepts build numbers, version numbers and two meta values `LATEST-EAP-SNAPSHOT`, `LATEST-TRUNK-SNAPSHOT`.
<br/>
Value may have `IC-` or `IU-` prefix in order to define IDEA distribution type. 
<br/><br/> 
**Default value**: `LATEST-EAP-SNAPSHOT`

- `intellij.type` defines the type of IDEA distribution: `IC` for community version and `IU` for ultimate.<br/><br/> 
**Default value**: `IC`

- `intellij.plugins` defines the list of bundled IDEA plugins and plugins from [idea repository](https://plugins.jetbrains.com/) 
that should be used as dependencies in format `org.plugin.id:version`. E.g. `plugins = ['org.intellij.plugins.markdown:8.5.0.20160208']`.
If version is not set then bundled plugin will be used.<br/><br/> 
**Default value:** `<empty>`

- `intellij.pluginName` is used for naming target zip-archive and defines the name of plugin artifact. 
of bundled IDEA plugins that should be used as dependencies.<br/><br/>
**Default value:** `$project.name`

- `intellij.sandboxDirectory` defines path of sandbox directory that is used for running IDEA with developing plugin.<br/><br/>
**Default value**: `$project.buildDir/idea-sandbox`

- `intellij.instrumentCode` defines whether plugin should instrument java classes with nullability assertions.
Also it might be required for compiling forms created by IntelliJ GUI designer.<br/><br/>
**Default value**: `true`

- `intellij.updateSinceUntilBuild` defines whether plugin should patch `plugin.xml` with since and until build values, 
if true then `IntelliJIDEABuildNumber` will be used as a `since` value and `IntelliJIDEABranch.9999` will be used as an until value.<br/><br/>
**Default value**: `true`

- `intellij.sameSinceUntilBuild` defines whether plugin should patch `plugin.xml` with "open" until build. 
if true then the same `IntelliJIDEABuildNumber` will be used as a `since` value and as an until value, 
which is useful for building plugins against EAP IDEA builds.<br/><br/>
**Default value**: `false`

- `intellij.downloadSources` defines whether plugin should download IntelliJ sources while 
initializing Gradle build. Since sources are no needed while testing on CI, you can set
it to `false` for particular environment.<br/><br/>
**Default value**: `true`

- `intellij.systemProperties` defines the map of system properties which will be passed to IDEA instance on
executing `runIdea` task and tests.<br/>
Also you can use `intellij.systemProperty(name, value)` method in order to set single system property.
<br/><br/>
**Default value**: `[]`

- `intellij.alternativeIdePath` – absolute path to the locally installed JetBrains IDE.
It makes sense to use this property if you want to test your plugin in WebStorm or any other non-IDEA JetBrains IDE.
Empty value means that the IDE that was used for compiling will be used for running/debugging as well.<br/><br/>
**Default value**: `<empty>`

- `intellij.ideaDependencyCachePath` – absolute path to the local directory that should be used for storing IDEA
distributions. If empty – Gradle cache directory will be used.
**Default value**: `<empty>`

### Publishing plugin

- `intellij.publish.username` your login at JetBrains plugin repository.
- `intellij.publish.password` your password at JetBrains plugin repository.
- `intellij.publish.channel` defines channel to upload, you may use any string here, empty string means default channel.

*Available in SNAPSHOT only*
- `intellij.publish.channels` defines several channels to upload, you may use any string here, `default` string means default channel.
<br/><br/>
**Default value**: `<empty>`

