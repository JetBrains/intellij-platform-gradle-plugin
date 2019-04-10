<a name="documentr_top"></a>[![official JetBrains project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) [![Join the chat at https://gitter.im/JetBrains/gradle-intellij-plugin](https://badges.gitter.im/JetBrains/gradle-intellij-plugin.svg)](https://gitter.im/JetBrains/gradle-intellij-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Gradle Plugin Release](https://img.shields.io/badge/gradle%20plugin-0.4.7-blue.svg)](https://plugins.gradle.org/plugin/org.jetbrains.intellij) [![GitHub Release](https://img.shields.io/github/release/jetbrains/gradle-intellij-plugin.svg)](https://github.com/jetbrains/gradle-intellij-plugin/releases) 

# gradle-intellij-plugin

#### The latest version is 0.4.7

> 
**This project requires Gradle 3.4 or newer**

This plugin allows you to build plugins for IntelliJ platform using specific IntelliJ SDK and bundled plugins.

The plugin adds extra IntelliJ-specific dependencies, patches processResources tasks to fill some tags 
(name, version) in `plugin.xml` with appropriate values, patches compile tasks to instrument code with 
nullability assertions and forms classes made with IntelliJ GUI Designer and provides some build steps which might be
helpful while developing plugins for IntelliJ platform.



# Getting started

Here is [the manual](https://www.jetbrains.org/intellij/sdk/docs/tutorials/build_system/prerequisites.html) on how
to start developing plugins for IntelliJ IDEA using Gradle.

# Usage
 
## Gradle 

```groovy
plugins {
  id "org.jetbrains.intellij" version "0.4.7"
}
```
 
### Snapshot version

<summary>Use the following code to get the latest features</summary>

```groovy
buildscript {
  repositories {
    mavenCentral()
    maven {
      url "https://oss.sonatype.org/content/repositories/snapshots/"
    }
    maven { 
      url 'https://dl.bintray.com/jetbrains/intellij-plugin-service' 
    }
    
  }
  dependencies {
    classpath "org.jetbrains.intellij.plugins:gradle-intellij-plugin:0.5.0-SNAPSHOT"
  }
}

apply plugin: 'org.jetbrains.intellij'
```
</details>

### Tasks

Plugin introduces the following tasks

| **Task** | **Description** |
| -------- | --------------- |
| `buildPlugin`            | Assembles plugin and prepares zip archive for deployment. |
| `patchPluginXml`         | Collects all plugin.xml files in sources and fill since/until build and version attributes. |
| `prepareSandbox`         | Creates proper structure of plugin, copies patched plugin xml files and fills sandbox directory with all of it. |
| `prepareTestingSandbox`  | Prepares sandbox that will be used while running tests. |
| `buildSearchableOptions` | Builds an index of UI components (a.k.a. searchable options) for the plugin by running a headless IDE instance. |
| `jarSearchableOptions`   | Creates a jar file with searchable options to be distributed with the plugin. |
| `runIde`                 | Executes an IntelliJ IDEA instance with the plugin you are developing. |
| `publishPlugin`          | Uploads plugin distribution archive to https://plugins.jetbrains.com. |
| `verifyPlugin`           | Validates plugin.xml and plugin's structure. |

## Configuration

Plugin provides following options to configure target IntelliJ SDK and build archive

### Setup DSL

The following attributes are part of the Setup DSL <kbd>intellij { ... }</kbd> in which allows you to setup the environment and dependencies.

| **Attributes** | **Values** | 
| :------------- | :--------- | 
| <kbd>pluginName</kbd> - The name of the target zip-archive and defines the name of plugin artifact.|**Acceptable Values:** <br/><kbd>String</kbd> - `'gradle-intellij-plugin'` <br/><br/>**Default Value:** <kbd>$project.name</kbd>|
| <kbd>version</kbd> - The version of the IDE distribution that should be used as a dependency. <br/><br/>**Notes:**    <ul>        <li>Value may have `IC-`, `IU-`, `CL-`, `RD` or `JPS-` prefix in order to define IDE distribution type.</li>        <li>`intellij.version` and `intellij.localPath` should not be specified at the same time.</li>    </ul>|**Acceptable Values:**    <ul>        <li><kbd>build #</kbd><br/>`'2017.2.5'` or `'IC-2017.2.5'` </li>        <li><kbd>version #</kbd><br/>`'172.4343'` or `'IU-172.4343'` </li>        <li><kbd>'LATEST-EAP-SNAPSHOT'</kbd></li>    </ul>**Default Value:** <kbd>'LATEST-EAP-SNAPSHOT'</kbd>|
| <kbd>type</kbd> - The type of IDE distribution.|**Acceptable Values:**    <ul>        <li><kbd>'IC'</kbd> - IntelliJ IDEA Community Edition. </li>        <li><kbd>'IU'</kbd> - IntelliJ IDEA Ultimate Edition. </li>        <li><kbd>'JPS'</kbd> - JPS-only. </li>        <li><kbd>'CL'</kbd> - CLion. </li>        <li><kbd>'RD'</kbd> - Rider.</li>        <li><kbd>'MPS'</kbd> - MPS.</li>    </ul>**Default Value:** <kbd>'IC'</kbd>|
| <kbd>plugins</kbd> - The list of bundled IDE plugins and plugins from the [JetBrains Plugin Repository](https://plugins.jetbrains.com/). <br/><br/>**Notes:**    <ul>        <li>For plugins from the JetBrains Plugin Repository use format `pluginId:version`.</li>        <li>For bundled plugins use directory name of the plugin in IDE distribution (e.g. `Groovy` for `IDEA/plugins/Groovy`).</li>        <li>For sub-projects use project reference `project(':subproject')`.</li>    </ul>|**Acceptable Values:**    <ol>        <li><kbd>org.plugin.id:version[@channel]</kbd><br/>`'org.intellij.plugins.markdown:8.5.0', 'org.intellij.scala:2017.2.638@nightly'`</li>        <li><kbd>bundledPluginName</kbd><br/>`'android', 'Groovy'`</li>        <li><kbd>project(':projectName')</kbd><br/>`project(':plugin-subproject')`</li>    </ol>**Default Value\:** none|
| <kbd>updateSinceUntilBuild</kbd> - Should plugin patch `plugin.xml` with since and until build values? <br/><br/>**Notes:**    <ul>        <li>If `true` then user-defined values from `patchPluginXml.sinceBuild` and `patchPluginXml.untilBuild` will be used (or their default values if none set). </li>    </ul>|**Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd>|
| <kbd>sameSinceUntilBuild</kbd> - Should plugin patch `plugin.xml` with an until build value that is just an "open" since build?  <br/><br/>**Notes:**    <ul>        <li>Is useful for building plugins against EAP IDE builds.</li>        <li>If `true` then the user-defined value from `patchPluginXml.sinceBuild` (or its default value) will be used as a `since` and an "open" `until` value. </li>        <li>If `patchPluginXml.untilBuild` has a value set, then `sameSinceUntilBuild` is ignored.</li>    </ul>|**Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>false</kbd>|
| <kbd>instrumentCode</kbd> - Should plugin instrument java classes with nullability assertions and compile forms created by IntelliJ GUI Designer?|**Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd>|
| <kbd>downloadSources</kbd> - Should plugin download IntelliJ sources while initializing Gradle build? <br/><br/>**Notes:**    <ul>        <li>Since sources are not needed while testing on CI, you can set it to `false` for a particular environment.</li>    </ul>|**Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd> if `CI` environment variable is not set|
| <kbd>localPath</kbd> - The path to locally installed IDE distribution that should be used as a dependency. <br/><br/>**Notes:**    <ul>        <li>`intellij.version` and `intellij.localPath` should not be specified at the same time.</li>    </ul>|**Acceptable Values:** <br/><kbd>path</kbd> - `'/Applications/IntelliJIDEA.app'`</br></br>**Default Value:** <kbd>null</kbd>|
| <kbd>sandboxDirectory</kbd> - The path of sandbox directory that is used for running IDE with developing plugin.|**Acceptable Values:** <br/><kbd>path</kbd> - `'${project.rootDir}/.sandbox'` <br/><br/>**Default Value:** <kbd>'${project.buildDir}/idea-sandbox'</kbd>|
| <kbd>alternativeIdePath</kbd> - The absolute path to the locally installed JetBrains IDE. <br/><br/>**Notes:**    <ul>        <li>Use this property if you want to test your plugin in any non-IDEA JetBrains IDE such as WebStorm or Android Studio.</li>        <li>Empty value means that the IDE that was used for compiling will be used for running/debugging as well.</li>    </ul>|**Acceptable Values:** <br/><kbd>path</kbd> - `'/Applications/Android Studio.app'`<br/><br/>**Default Value:** none|
| <kbd>ideaDependencyCachePath</kbd> - The absolute path to the local directory that should be used for storing IDE distributions. <br/><br/>**Notes:**    <ul>        <li>Empty value means the Gradle cache directory will be used.</li>    </ul>|**Acceptable Values:** <br/><kbd>path</kbd> - `'<example>'`<br/><br/>**Default Value:** none|
| <kbd>intellijRepo</kbd>, <kbd>pluginsRepo</kbd>, <kbd>jreRepo</kbd> - Urls of repositories for downloading IDE distributions, plugin dependencies and JetBrains Java Runtime. <br/><br/>|**Acceptable Values:** <br/><kbd>url</kbd><br/><br/>**Default Value:** <kbd>jetbrains.com/intellij-repository</kbd>, <kbd>plugins.jetbrains.com/maven</kbd>, <kbd>jetbrains.bintray.com/intellij-jdk</kbd>|


### Running DSL

`runIde` task extends [JavaExec](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html) Gradle task,
all configuration attributes of `JavaExec` task can be used in `runIde` as well.

In addition to that, following attributes may be used to customize IDE running:

| **Attributes**              | **Default Value**  |
| :-------------------------- | :----------------- |
| <kbd>jbrVersion</kbd> JetBrains Java version to use | **Acceptable Values:** <kbd>String</kbd> - E.g. `'8u112b752.4'`, `'8u202b1483.24'`, or `'11_0_2b159'`. Prefixes `jbrex`, `jbrx` or `jbr` are allowed.<br/><br/>All JetBrains Java version are available at [BitTray](https://bintray.com/jetbrains/intellij-jdk/).<br/><br/>**Default Value:** <kdb>null</kdb> for IDE &lt; 2017.3, <kdb>builtin java version</kdb>  for IDE &gt;= 2017.3 |
| <kbd>ideaDirectory</kbd> Path to IDE distribution | path to IDE-dependency |
| <kbd>configDirectory</kbd> Path to configuration directory | <kbd>${intellij.sandboxDirectory}/config</kbd> |
| <kbd>pluginsDirectory</kbd> Path to plugins directory | <kbd>${intellij.sandboxDirectory}/plugins</kbd> |
| <kbd>systemDirectory</kbd> Path to indexes directory | <kbd>${intellij.sandboxDirectory}/system</kbd> |

### Patching DSL
The following attributes are apart of the Patching DSL <kbd>patchPluginXml { ... }</kbd> in which allows Gradle to patch specific attributes in a set of `plugin.xml` files.

| **Attributes**            | **Default Value** |
| :------------------------ |  :---------------- |
| <kbd>version</kbd> is a value for the `<version>` tag.                                | <kbd>project.version</kbd> |
| <kbd>sinceBuild</kbd> is for the `since-build` attribute of the `<idea-version>` tag. | <kbd>IntelliJIDEBuildNumber</kbd> |
| <kbd>untilBuild</kbd> is for the `until-build` attribute of the `<idea-version>` tag. | <kbd>IntelliJIDEBranch.*</kbd> |
| <kbd>pluginDescription</kbd> is for the `<description>` tag.                          | none |
| <kbd>changeNotes</kbd> is for the `<change-notes>` tag.                               | none |
| <kbd>pluginXmlFiles</kbd> is a collection of xml files to patch.                      | All `plugin.xml` files with `<idea-plugin>` |
| <kbd>destinationDir</kbd> is a directory to store patched xml files.                  | <kbd>'${project.buildDir}/patchedPluginXmlFiles'</kbd> |

### Publishing DSL
The following attributes are apart of the Publishing DSL <kbd>publishPlugin { ... }</kbd> in which allows Gradle to upload a working plugin to the JetBrains Plugin Repository.

See the instruction on how to generate authentication token: https://www.jetbrains.org/intellij/sdk/docs/plugin_repository/api/plugin_upload.html

| **Attributes**              | **Default Value**  |
| :-------------------------- | :----------------- |
| <kbd>token</kbd> Authentication token | none |
| <kbd>username</kbd> Login username | none |
| <kbd>password</kbd> Login password | none |
| <kbd>channels</kbd> List of channel names to upload plugin to.  | <kbd>[default]</kbd> |
| <kbd>host</kbd>  URL host of a plugin repository.               | <kbd>https://plugins.jetbrains.com</kbd> |
| <kbd>distributionFile</kbd> Jar or Zip file of plugin to upload | output of `buildPlugin` task |

### Instrumenting DSL
The following attributes help you to tune instrumenting behaviour in <kbd>instrumentCode { ... }</kbd> block.

| **Attributes**            | **Default Value** |
| :------------------------ |  :---------------- |
| <kbd>compilerVersion</kbd> is a version of instrumenting compiler. It's used for non-IDEA plugins (e.g. CLion or Rider). | <kbd>Build number of the IDE dependency</kbd> |

### build.gradle

```groovy
plugins {
  id "org.jetbrains.intellij" version "0.4.7"
}

intellij {
  version 'IC-2018.3'
  plugins = ['coverage', 'org.intellij.plugins.markdown:8.5.0.20160208']
  pluginName 'MyPlugin'

}
publishPlugin {
  username 'zolotov'
  password 'password'
  channels 'nightly'
} 
```

# Examples

As examples of using this plugin you can check out following projects:

- [Go plugin](https://github.com/go-lang-plugin-org/go-lang-idea-plugin) and its [TeamCity build configuration](https://teamcity.jetbrains.com/project.html?projectId=IntellijIdeaPlugins_Go&tab=projectOverview)
- [Erlang plugin](https://github.com/ignatov/intellij-erlang) and its [TeamCity build configuration](https://teamcity.jetbrains.com/project.html?projectId=IntellijIdeaPlugins_Erlang&tab=projectOverview)
- [Rust plugin](https://github.com/intellij-rust/intellij-rust) and its [TeamCity build configuration](https://teamcity.jetbrains.com/project.html?projectId=IntellijIdeaPlugins_Rust&tab=projectOverview)
- [AWS CloudFormation plugin](https://github.com/shalupov/idea-cloudformation) and its [TeamCity build configuration](https://teamcity.jetbrains.com/project.html?projectId=IdeaAwsCloudFormation&tab=projectOverview)
- [Bash plugin](https://github.com/jansorg/BashSupport) and its [TeamCity build configuration](https://teamcity.jetbrains.com/project.html?projectId=IntellijIdeaPlugins_BashSupport&tab=projectOverview)
- [Perl5 plugin](https://github.com/hurricup/Perl5-IDEA) and its [Travis configuration file](https://github.com/hurricup/Perl5-IDEA/blob/master/.travis.yml)
- [Bamboo Soy plugin](https://github.com/google/bamboo-soy) and its [Travis configuration file](https://github.com/google/bamboo-soy/blob/master/.travis.yml)
- [Android Drawable Importer plugin](https://github.com/winterDroid/android-drawable-importer-intellij-plugin)
- [Android Material Design Icon Generator plugin](https://github.com/konifar/android-material-design-icon-generator-plugin)
- [AceJump plugin](https://github.com/johnlindquist/AceJump)  
	- Uses the Gradle Kotlin DSL
- [EmberJS plugin](https://github.com/Turbo87/intellij-emberjs)
- [HCL plugin](https://github.com/VladRassokhin/intellij-hcl)
- [Robot plugin](https://github.com/AmailP/robot-plugin)
- [TOML plugin](https://github.com/stuartcarnie/toml-plugin)
- [SQLDelight Android Studio Plugin](https://github.com/square/sqldelight/tree/master/sqldelight-idea-plugin)
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
- [Unity 3D plugin](https://github.com/JetBrains/resharper-unity) for JetBrains Rider
- [AEM Tools plugin](https://github.com/aemtools/aemtools) for Adobe Experience Manager integration
	- Uses the Gradle Kotlin DSL
	- Fully written in kotlin
	- Uses template language
- [F# plugin](https://github.com/JetBrains/fsharp-support/tree/master/rider-fsharp) for JetBrains Rider
	- Uses the Gradle Kotlin DSL
- [Intellij Rainbow Brackets](https://github.com/izhangzhihao/intellij-rainbow-brackets)
	- Fully written in kotlin
	- Uses other intellij plugins as test dependencies
	- Circle CI configuration file & Travis CI configuration file
	- Gradle task to verify plugin compatibility cross intellij versions
	- Auto subbmit anonymous feedback as github issues

# License


```
Copyright 2019 org.jetbrains.intellij.plugins

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


--

> `This README.md file was hand-crafted with care utilising synapticloop`[`templar`](https://github.com/synapticloop/templar/)`->`[`documentr`](https://github.com/synapticloop/documentr/)

--

