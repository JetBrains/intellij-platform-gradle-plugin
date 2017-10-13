[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) [![Join the chat at https://gitter.im/JetBrains/gradle-intellij-plugin](https://badges.gitter.im/JetBrains/gradle-intellij-plugin.svg)](https://gitter.im/JetBrains/gradle-intellij-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Gradle Plugin Release](https://img.shields.io/badge/gradle%20plugin-0.2.17-blue.svg)](https://plugins.gradle.org/plugin/org.jetbrains.intellij) 

> **This project requires JVM version of at least 1.7**




# gradle-intellij-plugin



> 
This plugin allows you to build plugins for IntelliJ platform using specific IntelliJ SDK and bundled plugins.

The plugin adds extra IntelliJ-specific dependencies, patches processResources tasks to fill some tags 
(name, version) in `plugin.xml` with appropriate values, patches compile tasks to instrument code with 
nullability assertions and forms classes made with IntelliJ GUI Designer and provides some build steps which might be
helpful while developing plugins for IntelliJ platform.



# Usage

## Gradle

```groovy
plugins {
  id "org.jetbrains.intellij" version "0.2.17"
}
```

### Other Setups

<details>
<summary><b>Pre Gradle 2.1</b> - Use the following code when Gradle is not at version 2.1 or higher <em>(Click to expand)</em>...</summary>

```groovy
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "gradle.plugin.org.jetbrains.intellij.plugins:gradle-intellij-plugin:0.2.17"
  }
}

apply plugin: 'org.jetbrains.intellij'
```

</details>

<details>
<summary><b>SNAPSHOT</b> - Use the following code to get the lastest features <em>(Click to expand)</em>...</summary>

 ```groovy
buildscript {
  repositories {
    mavenCentral()
    maven {
      url "https://oss.sonatype.org/content/repositories/snapshots/"
    }
    maven {
      url 'http://dl.bintray.com/jetbrains/intellij-plugin-service' 
    }
    
  }
  dependencies {
    classpath "org.jetbrains.intellij.plugins:gradle-intellij-plugin:0.3.0-SNAPSHOT"
  }
}

apply plugin: 'org.jetbrains.intellij'
```

</details>

### Tasks

Plugin introduces the following tasks

| **Task** | **Description** |
| -------- | --------------- |
| `buildPlugin`    | Assembles plugin and prepares zip archive for deployment. |
| `patchPluginXml` | Collects all plugin.xml files in sources and fill since/until build and version attributes. |
| `prepareSandbox` | Creates proper structure of plugin, copies patched plugin xml files and fills sandbox directory with all of it. |
| `runIde`         | Executes an IntelliJ IDEA instance with the plugin you are developing. |
| `publishPlugin`  | Uploads plugin distribution archive to http://plugins.jetbrains.com. |

**Available in SNAPSHOT:**

| **Task** | **Description** |
| -------- | --------------- |
| `verifyPlugin` | Validates plugin.xml and plugin's structure. |
## Configuration

Plugin provides following options to configure target IntelliJ SDK and build archive

### intellij

| **Attribute**             | **Information** | **Values** |
| :-----------------------: | :-------------- | :--------- |
| `version`                 | The version of the IDEA distribution that should be used as a dependency. <br/><br/>**Notes:** `intellij.version` and `intellij.localPath` should not be specified at the same time. | **Acceptable Values:** <br/><ul><li><kbd>build #</kbd> - e.g. `example` </li><li><kbd>version #</kbd> - e.g. `example` </li><li><kbd>LATEST-EAP-SNAPSHOT</kbd></li><li><kbd>LATEST_TRUNK-SNAPSHOT</kbd></li></ul>**Default Value:** <kbd>LATEST-EAP-SNAPSHOT</kbd> |
| `localPath`               | The path to locally installed IDEA distribution that should be used as a dependency. <br/><br/>**Notes:** `intellij.version` and `intellij.localPath` should not be specified at the same time. | **Acceptable Values:** <br/><kbd>path</kbd><br/>`/Applications/IntelliJIDEA.app`</br>**Default Value:** <kbd>null</kbd> |
| `type`                    | The type of IDEA distribution. <br/><br/>**Notes:** notes. | **Acceptable Values:** <br/><ul><li><kbd>IC</kbd> - Community Edition. </li><li><kbd>IU</kbd> - Ultimate Edition. </li><li><kbd>JPS</kbd> - JPS-only. </li><li><kbd>RD</kbd> - Rider.</li></ul>**Default Value:** <kbd>IC</kbd> |
| `plugins`                 | The list of bundled IDEA plugins and plugins from the [IDEA repository](https://plugins.jetbrains.com/). <br/><br/>**Notes:** For plugins from the IDEA repository - `format 1`.<br/>For bundled plugins from the project - `format 2`.<br/>For sub-projects - `format 3`<br/><br/>Mix and match all types of acceptable values.  | **Acceptable Values:** <br/><ol><li><kbd>org.plugin.id:version[@channel]</kbd><br/>`['org.intellij.plugins.markdown: 8.5.0', 'org.intellij.scala: 2017.2.638@nightly']`</li><li><kbd>bundledPluginName</kbd><br/>`['android', 'Groovy']`</li><li><kbd>project(':projectName')</kbd><br/>`[project(':plugin-subproject')]`</li></ol>**Default Value:** none |
| `pluginName`              | The name of the target zip-archive and defines the name of plugin artifact. | **Acceptable Values:** <br/><kbd>String</kbd> - e.g. `gradle-intellij-plugin` <br/><br/>**Default Value:** <kbd>$project.name</kbd> |
| `sandboxDirectory`        | The path of sandbox directory that is used for running IDEA with developing plugin. | **Acceptable Values:** <br/><kbd>path</kbd> - e.g. `/build/sandbox` <br/><br/>**Default Value:** <kbd>$project.buildDir/idea-sandbox</kbd> |
| `instrumentCode`          | Should plugin instrument java classes with nullability assertions? <br/><br/>**Notes:** Instrumentation code cannot be performed while using Rider distributions `RD`. <br/> Might be required for compiling forms created by IntelliJ GUI designer. | **Acceptable Values:** <br/><kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd> |
| `updateSinceUntilBuild`   | Should plugin patch `plugin.xml` with since and until build values? <br/><br/>**Notes:** If `true` then user-defined values from `patchPluginXml.sinceBuild` and `patchPluginXml.untilBuild` will be used (or their default values if none set). | **Acceptable Values:** <br/><kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd> |
| `sameSinceUntilBuild`     | Should plugin patch `plugin.xml` with an until build value that is just an "open" since build?  <br/><br/>**Notes:** Is useful for building plugins against EAP IDEA builds. <br/> If `true` then the user-defined value from `patchPluginXml.sinceBuild` (or its default value) will be used as a `since` and `until` value ("open"). <br/> If `patchPluginXml.untilBuild` has a value set, then `sameSinceUntilBuild` is ignored.  |  **Acceptable Values:** <br/><kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>false</kbd> |
| `downloadSources`         | Should plugin download IntelliJ sources while initializing Gradle build? <br/><br/>**Notes:** Since sources are not needed while testing on CI, you can set it to `false` for a particular environment. | **Acceptable Values:** <br/><kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd> if `CI` environment variable is not set |
| `alternativeIdePath`      | The absolute path to the locally installed JetBrains IDE. <br/><br/>**Notes:** Use this property if you want to test your plugin in any non-IDEA JetBrains IDE such as WebStorm or Android Studio. <br/> Empty value means that the IDE that was used for compiling will be used for running/debugging as well. | **Acceptable Values:** <br/><kbd>path</kbd><br/>`/Applications/Android Studio.app`<br/><br/>**Default Value:** none | 
| `ideaDependencyCachePath` | The absolute path to the local directory that should be used for storing IDEA distributions.. <br/><br/>**Notes:** Empty value means the Gradle cache directory will be used. | **Acceptable Values:** <br/><kbd>path</kbd> - e.g. `example`</br>**Default Value:** none | 


##### Deprecated
| **Attribute**             | **Information** | **Values** |
| :-----------------------: | :-------------- | :--------- |
| `systemProperties`        | The map of system properties which will be passed to IDEA instance on executing `runIdea` task and tests. <br/><br/>**Notes:** Use `systemProperties` methods of a particular tasks like `runIde` or `test`. | **Acceptable Values:** <br/><br/><br/>**Default Value:** <kbd>[]</kbd> |


<!-- 

| `type` | Description. <br/><br/>**Notes:** notes. | **Acceptable Values:** <br/><kbd>item1</kbd> <kbd>item2</kbd><br/><br/>**Default Value:** <kbd>value</kbd> |

<ul><li><kbd>item1</kbd> item1Description.</li><li><kbd>item2</kbd> item2Description.</li></ul>

-->


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


### build.gradle

```groovy
plugins {
  id "org.jetbrains.intellij" version "0.2.17"
}

intellij {
  version 'IC-2016.1'
  plugins = ['coverage', 'org.intellij.plugins.markdown:8.5.0.20160208']
  pluginName 'MyPlugin'

}
publishPlugin {
  username 'zolotov'
  password 'password'
  channels 'nightly'
} 
```

# Getting started

Here is [the manual](http://www.jetbrains.org/intellij/sdk/docs/tutorials/build_system/prerequisites.html) on how
to start developing plugins for IntelliJ IDEA using Gradle.

# Examples

As examples of using this plugin you can check out following projects:

- [Go plugin](https://github.com/go-lang-plugin-org/go-lang-idea-plugin) and its [TeamCity build configuration](https://teamcity.jetbrains.com/project.html?projectId=IntellijIdeaPlugins_Go&tab=projectOverview)
- [Erlang plugin](https://github.com/ignatov/intellij-erlang) and its [TeamCity build configuration](https://teamcity.jetbrains.com/project.html?projectId=IntellijIdeaPlugins_Erlang&tab=projectOverview)
- [Rust plugin](https://github.com/intellij-rust/intellij-rust) and its [TeamCity build configuration](https://teamcity.jetbrains.com/project.html?projectId=IntellijIdeaPlugins_Rust&tab=projectOverview)
- [AWS CloudFormation plugin](https://github.com/shalupov/idea-cloudformation) and its [TeamCity build configuration](https://teamcity.jetbrains.com/project.html?projectId=IdeaAwsCloudFormation&tab=projectOverview)
- [Bash plugin](https://github.com/jansorg/BashSupport) and its [TeamCity build configuration](https://teamcity.jetbrains.com/project.html?projectId=IntellijIdeaPlugins_BashSupport&tab=projectOverview)
- [Perl5 plugin](https://github.com/hurricup/Perl5-IDEA) and its [Travis configuration file](https://github.com/hurricup/Perl5-IDEA/blob/master/.travis.yml)
- [Android Drawable Importer plugin](https://github.com/winterDroid/android-drawable-importer-intellij-plugin)
- [Android Material Design Icon Generator plugin](https://github.com/konifar/android-material-design-icon-generator-plugin)
- [AceJump plugin](https://github.com/johnlindquist/AceJump)  
	- Uses the Gradle Kotlin DSL
- [EmberJS plugin](https://github.com/Turbo87/intellij-emberjs)
- [GCloud plugin](https://github.com/GoogleCloudPlatform/gcloud-intellij)
- [HCL plugin](https://github.com/VladRassokhin/intellij-hcl)
- [Robot plugin](https://github.com/AmailP/robot-plugin)
- [TOML plugin](https://github.com/stuartcarnie/toml-plugin)
- [SQLDelight Android Studio Plugin](https://github.com/square/sqldelight/tree/master/sqldelight-studio-plugin)
- [idear plugin](https://github.com/breandan/idear)
  - Uses the Gradle Kotlin DSL
- [Android WiFi ADB plugin](https://github.com/pedrovgs/AndroidWiFiADB)
- [SonarLint plugin](https://github.com/SonarSource/sonar-intellij)
- [IdeaVim plugin](https://github.com/JetBrains/ideavim) and its [TeamCity build configuration](https://teamcity.jetbrains.com/project.html?projectId=IdeaVim&guest=1)
- [Adb Idea](https://github.com/pbreault/adb-idea) is configured to build and run against stable, beta or preview (canary) releases of Android Studio
- [Gerrit](https://github.com/uwolfer/gerrit-intellij-plugin) uses Travis CI inclusive automated publishing of releases to GitHub and JetBrains plugin repository (triggered by version tag creation)
- [.ignore](https://github.com/hsz/idea-gitignore)
- [Minecraft Development](https://github.com/minecraft-dev/MinecraftDev) and its [TeamCity build configuration](https://ci.demonwav.com/viewType.html?buildTypeId=MinecraftDevIntelliJ_Build)
  - Uses the Gradle Kotlin DSL
  - Mixes Java, Kotlin, and Groovy code
  - Uses Grammar Kit
  - Uses a Kotlin version not bundled with IntelliJ
- [Mainframer Integration](https://github.com/elpassion/mainframer-intellij-plugin)
	- Uses the Gradle Kotlin DSL
	- Fully written in kotlin
	- Uses RxJava
- [Unity 3D plugin](https://github.com/JetBrains/resharper-unity/tree/master/rider) for JetBrains Rider

# License


```
Copyright 2017 org.jetbrains.intellij.plugins

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

```

