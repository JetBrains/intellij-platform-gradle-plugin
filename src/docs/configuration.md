## Configuration

Plugin provides following options to configure target IntelliJ SDK and build archive

### intellij DSL

The following attributes are apart of the IntelliJ DSL in which allows you to set up the environment and dependencies.
```groovy
intellij {
    // Attributes here
}
```

| **Attributes**             | **Values** |
| :-----------------------  | :--------- |
| <kbd>pluginName</kbd> - The name of the target zip-archive and defines the name of plugin artifact. | **Acceptable Values:** <br/><kbd>String</kbd> - `gradle-intellij-plugin` <br/><br/>**Default Value:** <kbd>$project.name</kbd> |
| <kbd>version</kbd> - The version of the IDEA distribution that should be used as a dependency. <br/><br/>**Notes:** Value may have `IC-`, `IU-` or `JPS-` prefix in order to define IDEA distribution type. <br/><br/>`intellij.version` and `intellij.localPath` should not be specified at the same time. | **Acceptable Values:** <br/><li><kbd>build #</kbd> - `2017.2.5` </li><li><kbd>version #</kbd> - `172.4343.14` </li><li><kbd>LATEST-EAP-SNAPSHOT</kbd></li><li><kbd>LATEST_TRUNK-SNAPSHOT</kbd></li>**Default Value:** <kbd>LATEST-EAP-SNAPSHOT</kbd> |
| <kbd>type</kbd> - The type of IDEA distribution. | **Acceptable Values:** <br/><li><kbd>IC</kbd> - Community Edition. </li><li><kbd>IU</kbd> - Ultimate Edition. </li><li><kbd>JPS</kbd> - JPS-only. </li><li><kbd>RD</kbd> - Rider.</li>**Default Value:** <kbd>IC</kbd> |
| <kbd>updateSinceUntilBuild</kbd> - Should plugin patch `plugin.xml` with since and until build values? <br/><br/>**Notes:** If `true` then user-defined values from `patchPluginXml.sinceBuild` and `patchPluginXml.untilBuild` will be used (or their default values if none set). | **Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd> |
| <kbd>sameSinceUntilBuild</kbd> - Should plugin patch `plugin.xml` with an until build value that is just an "open" since build?  <br/><br/>**Notes:** Is useful for building plugins against EAP IDEA builds. <br/><br/> If `true` then the user-defined value from `patchPluginXml.sinceBuild` (or its default value) will be used as a `since` and an "open" `until` value. <br/><br/> If `patchPluginXml.untilBuild` has a value set, then `sameSinceUntilBuild` is ignored.  |  **Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>false</kbd> |
| <kbd>instrumentCode</kbd> - Should plugin instrument java classes with nullability assertions? <br/><br/>**Notes:** Instrumentation code cannot be performed while using Rider distributions `RD`. <br/><br/> Might be required for compiling forms created by IntelliJ GUI designer. | **Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd> |
| <kbd>downloadSources</kbd> - Should plugin download IntelliJ sources while initializing Gradle build? <br/><br/>**Notes:** Since sources are not needed while testing on CI, you can set it to `false` for a particular environment. | **Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd> if `CI` environment variable is not set |
| <kbd>plugins</kbd> -The list of bundled IDEA plugins and plugins from the [IDEA repository](https://plugins.jetbrains.com/). <br/><br/>**Notes:** <li>For plugins from the IDEA repository use `format 1`.</li><li>For bundled plugins from the project use `format 2`.</li><li>For sub-projects use `format 3`</li><br/>Mix and match all types of acceptable values.  |**Acceptable Values:** <br/><ol><li><kbd>org.plugin.id:version[@channel]</kbd><br/>`['org.intellij.plugins.markdown:8.5.0', 'org.intellij.scala:2017.2.638@nightly']`</li><li><kbd>bundledPluginName</kbd><br/>`['android', 'Groovy']`</li><li><kbd>project(':projectName')</kbd><br/>`[project(':plugin-subproject')]`</li></ol><br/><b>Default Value:</b> none |
| <kbd>localPath</kbd> - The path to locally installed IDEA distribution that should be used as a dependency. <br/><br/>**Notes:** `intellij.version` and `intellij.localPath` should not be specified at the same time. | **Acceptable Values:** <br/><kbd>path</kbd> - `/Applications/IntelliJIDEA.app`</br>**Default Value:** <kbd>null</kbd> |
| <kbd>sandboxDirectory</kbd> - The path of sandbox directory that is used for running IDEA with developing plugin. | **Acceptable Values:** <br/><kbd>path</kbd> - `/build/sandbox` <br/><br/>**Default Value:** <kbd>$project.buildDir/idea-sandbox</kbd> |
| <kbd>alternativeIdePath</kbd> - The absolute path to the locally installed JetBrains IDE. <br/><br/>**Notes:** Use this property if you want to test your plugin in any non-IDEA JetBrains IDE such as WebStorm or Android Studio. <br/> Empty value means that the IDE that was used for compiling will be used for running/debugging as well. | **Acceptable Values:** <br/><kbd>path</kbd> - `/Applications/Android Studio.app`<br/><br/>**Default Value:** none |
| <kbd>ideaDependencyCachePath</kbd> - The absolute path to the local directory that should be used for storing IDEA distributions. <br/><br/>**Notes:** Empty value means the Gradle cache directory will be used. | **Acceptable Values:** <br/><kbd>path</kbd> - `example`<br/><br/> **Default Value:** none |

##### Deprecated
| **Attribute**             | **Values** |
| :------------------------ | :--------- |
| <kbd>systemProperties</kbd> - The map of system properties which will be passed to IDEA instance on executing `runIdea` task and tests. <br/><br/>**Notes:** Use `systemProperties` methods of a particular tasks like `runIde` or `test`. | **Acceptable Values:** <br/><br/><br/>**Default Value:** <kbd>[]</kbd> |

<!--
| <kbd>attribute</kbd> - Description. <br/><br/>**Notes:** notes. | **Acceptable Values:** <br/><kbd>item1</kbd> <kbd>item2</kbd><br/><br/> **Default Value:** <kbd>value</kbd> |
<li><kbd>item1</kbd> item1Description.</li><li><kbd>item2</kbd> item2Description.</li>
-->

### Patching DSL
The following attributes are apart of the Patching DSL in which allows Gradle to patch specific attributes in a set of `plugin.xml` files.
```groovy
patchPluginXml {
    // Attributes here
}
```
| **Attribute**             | **Default Value** |
| :------------------------ |  :---------------- |
| <kbd>version</kbd> is a value for the `<version>` tag.                                | <kbd>project.version</kbd> |
| <kbd>sinceBuild</kbd> is for the `since-build` attribute of the `<idea-version>` tag. | <kbd>IntelliJIDEABuildNumber</kbd> |
| <kbd>untilBuild</kbd> is for the `until-build` attribute of the `<idea-version>` tag. | <kbd>IntelliJIDEABranch.*</kbd> |
| <kbd>pluginDescription</kbd> is for the `<description>` tag.                          | none |
| <kbd>pluginXmlFiles</kbd> is a collection of xml files to patch.                      | All `plugin.xml` files with `<idea-plugin>` |
| <kbd>destinationDir</kbd> is a directory to store patched xml files.                  | <kbd>$project.buildDir/patchedPluginXmlFiles</kbd> |

### Publishing DSL
The following attributes are apart of the Publishing DSL in which allows Gradle to upload a working plugin to the JetBrain Plugin Repository.
```groovy
publishPlugin {
    // Attributes here
}
```

| **Attribute**               | **Default Value**  |
| :-------------------------- | :----------------- |
| <kbd>username</kbd> Login username | none |
| <kbd>password</kbd> Login password | none |
| <kbd>channels</kbd> List of channel names to upload plugin to.  | <kbd>[default]</kbd> |
| <kbd>host</kbd>  URL host of a plugin repository.               | <kbd>http://plugins.jetbrains.com</kbd> |
| <kbd>distributionFile</kbd> Jar or Zip file of plugin to upload | output of `buildPlugin` task |

##### Deprecated
```groovy
intelliJ {
    publish {
        // Deprecated
    }
}
```
| **Attribute**               | **Default Value**  |
| :-------------------------- | :----------------- |
| <kbd>username</kbd> Login username | none |
| <kbd>password</kbd> Login password | none |
| <kbd>channel</kbd> A single channel name to upload plugin to.   | <kbd>default</kbd> |
| <kbd>channels</kbd> List of comma-separated channel names to upload plugin to.  | <kbd>default</kbd> |


