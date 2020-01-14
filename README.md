<a name="documentr_top"></a>[![official JetBrains project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) [![Join the chat at https://gitter.im/JetBrains/gradle-intellij-plugin](https://img.shields.io/badge/chat-%20%20-brightgreen.svg)](https://gitter.im/JetBrains/gradle-intellij-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Gradle Plugin Release](https://img.shields.io/badge/gradle%20plugin-0.4.13-blue.svg)](https://plugins.gradle.org/plugin/org.jetbrains.intellij) [![GitHub Release](https://img.shields.io/github/release/jetbrains/gradle-intellij-plugin.svg)](https://github.com/jetbrains/gradle-intellij-plugin/releases) 

# gradle-intellij-plugin

<h4><a id="the-latest-version" class="anchor" aria-hidden="true" href="#the-latest-version"><svg class="octicon octicon-link" viewBox="0 0 16 16" version="1.1" width="16" height="16" aria-hidden="true"><path fill-rule="evenodd" d="M4 9h1v1H4c-1.5 0-3-1.69-3-3.5S2.55 3 4 3h4c1.45 0 3 1.69 3 3.5 0 1.41-.91 2.72-2 3.25V8.59c.58-.45 1-1.27 1-2.09C10 5.22 8.98 4 8 4H4c-.98 0-2 1.22-2 2.5S3 9 4 9zm9-3h-1v1h1c1 0 2 1.22 2 2.5S13.98 12 13 12H9c-.98 0-2-1.22-2-2.5 0-.83.42-1.64 1-2.09V6.25c-1.09.53-2 1.84-2 3.25C6 11.31 7.55 13 9 13h4c1.45 0 3-1.69 3-3.5S14.5 6 13 6z"></path></svg></a>The latest version is 0.4.13</h4>

> 
**This project requires Gradle 3.4 or newer**

This plugin allows you to build plugins for IntelliJ platform using specified IntelliJ SDK and bundled/3rd-party plugins.

The plugin adds extra IntelliJ-specific dependencies, patches `processResources` tasks to fill some tags 
(name, version) in `plugin.xml` with appropriate values, patches compile tasks to instrument code with 
nullability assertions and forms classes made with IntelliJ GUI Designer and provides some build steps which might be
helpful while developing plugins for IntelliJ platform.



# Getting started

Here is [the manual](https://www.jetbrains.org/intellij/sdk/docs/tutorials/build_system/prerequisites.html) on how
to start developing plugins for IntelliJ Platform using Gradle.

Also, please take a look at [the FAQ](../../blob/master/FAQ.md).

# Usage
 
## Gradle 

```groovy
plugins {
  id "org.jetbrains.intellij" version "0.4.13"
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
| `buildSearchableOptions` | Builds an index of UI components (a.k.a. searchable options) for the plugin by running a headless IDE instance.<br>Note, that this is a `runIde` task with predefined arguments and all properties of `runIde` task are also applied to `buildSearchableOptions` tasks. |
| `jarSearchableOptions`   | Creates a jar file with searchable options to be distributed with the plugin. |
| `runIde`                 | Executes an IntelliJ IDEA instance with the plugin you are developing. |
| `publishPlugin`          | Uploads plugin distribution archive to https://plugins.jetbrains.com. |
| `verifyPlugin`           | Validates completeness and contents of plugin.xml descriptors as well as plugin’s archive structure. |

## Configuration

Plugin provides the following options to configure target IntelliJ SDK and build archive

### Setup DSL

The following attributes are a part of the Setup DSL <kbd>intellij { ... }</kbd> in which allows you to setup the environment and dependencies.

| Attributes | Values | 
| :------------- | :--------- | 
| <kbd>pluginName</kbd> - The name of the target zip-archive and defines the name of plugin artifact.|**Acceptable Values:** <br/><kbd>String</kbd> - `'gradle-intellij-plugin'` <br/><br/>**Default Value:** <kbd>$project.name</kbd>|

#### IntelliJ Platform Properties
| Attributes | Values | 
| :------------- | :--------- | 
| <kbd>version</kbd> - The version of the IntelliJ Platform IDE that will be used to build the plugin. <br/><br/>Please see [Plugin Compatibility](https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/plugin_compatibility.html) in SDK docs for more details.<br/><br/>**Notes:**    <ul>        <li>Value may have `IC-`, `IU-`, `CL-`, `PY-`, `PC-`, `RD` or `JPS-` prefix in order to define IDE distribution type.</li>        <li>`intellij.version` and `intellij.localPath` should not be specified at the same time.</li>    </ul>|**Acceptable Values:**    <ul>        <li><kbd>version #</kbd><br/>`'2017.2.5'` or `'IC-2017.2.5'` </li>        <li><kbd>build #</kbd><br/>`'172.4343'` or `'IU-172.4343'` </li>        <li><kbd>'LATEST-EAP-SNAPSHOT'</kbd></li>    </ul><br/><br/>All available JetBrains IDEs versions can be found at [Intellij Artifacts](https://www.jetbrains.org/intellij/sdk/docs/reference_guide/intellij_artifacts.html) page.<br/><br/>**Default Value:** <kbd>'LATEST-EAP-SNAPSHOT'</kbd>|
| <kbd>type</kbd> - The type of IDE distribution.|**Acceptable Values:**    <ul>        <li><kbd>'IC'</kbd> - IntelliJ IDEA Community Edition. </li>        <li><kbd>'IU'</kbd> - IntelliJ IDEA Ultimate Edition. </li>        <li><kbd>'JPS'</kbd> - JPS-only. </li>        <li><kbd>'CL'</kbd> - CLion. </li>        <li><kbd>'PY'</kbd> - PyCharm Professional Edition. </li>        <li><kbd>'PC'</kbd> - PyCharm Community Edition. </li>        <li><kbd>'RD'</kbd> - Rider.</li>    </ul>**Default Value:** <kbd>'IC'</kbd>|
| <kbd>localPath</kbd> - The path to locally installed IDE distribution that should be used as a dependency. <br/><br/>**Notes:**    <ul>        <li>`intellij.version` and `intellij.localPath` should not be specified at the same time.</li>    </ul>|**Acceptable Values:** <br/><kbd>path</kbd> - `'/Applications/IntelliJIDEA.app'`</br></br>**Default Value:** <kbd>null</kbd>|
| <kbd>plugins</kbd> - The list of bundled IDE plugins and plugins from the [JetBrains Plugin Repository](https://plugins.jetbrains.com/). <br/><br/>Please see [Plugin Dependencies](http://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_dependencies.html) in SDK docs for more details.<br/><br/>**Notes:**    <ul>        <li>For plugins from the JetBrains Plugin Repository use format `pluginId:version`.</li>        <li>For bundled plugins use directory name of the plugin in IDE distribution (e.g. `Groovy` for `IDEA/plugins/Groovy`).</li>        <li>For sub-projects use project reference `project(':subproject')`.</li>        <li>If you need to refer plugin's classes from your project, you also have to define a dependency in your `plugin.xml`.</li>    </ul>|**Acceptable Values:**    <ol>        <li><kbd>org.plugin.id:version[@channel]</kbd><br/>`'org.intellij.plugins.markdown:8.5.0', 'org.intellij.scala:2017.2.638@nightly'`</li>        <li><kbd>bundledPluginName</kbd><br/>`'android', 'Groovy'`</li>        <li><kbd>project(':projectName')</kbd><br/>`project(':plugin-subproject')`</li>    </ol>**Default Value\:** none|

#### Building Properties
| Attributes | Values | 
| :------------- | :--------- | 
| <kbd>updateSinceUntilBuild</kbd> - Should plugin patch `plugin.xml` with since and until build values? <br/><br/>**Notes:**    <ul>        <li>If `true` then user-defined values from `patchPluginXml.sinceBuild` and `patchPluginXml.untilBuild` will be used (or their default values if none set). </li>    </ul>|**Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd>|
| <kbd>sameSinceUntilBuild</kbd> - Should plugin patch `plugin.xml` with an until build value that is just an "open" since build?  <br/><br/>**Notes:**    <ul>        <li>Is useful for building plugins against EAP IDE builds.</li>        <li>If `true` then the user-defined value from `patchPluginXml.sinceBuild` (or its default value) will be used as a `since` and an "open" `until` value. </li>        <li>If `patchPluginXml.untilBuild` has a value set, then `sameSinceUntilBuild` is ignored.</li>    </ul>|**Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>false</kbd>|
| <kbd>instrumentCode</kbd> - Should plugin instrument java classes with nullability assertions and compile forms created by IntelliJ GUI Designer?|**Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd>|

#### Run/Debug IDE Properties
| Attributes | Values | 
| :------------- | :--------- | 
| <kbd>alternativeIdePath</kbd> - The absolute path to the locally installed JetBrains IDE. <br/><br/>**Notes:**    <ul>        <li>Use this property if you want to test your plugin in any non-IDEA JetBrains IDE such as WebStorm or Android Studio.</li>        <li>Empty value means that the IDE that was used for compiling will be used for running/debugging as well.</li>    </ul>|**Acceptable Values:** <br/><kbd>path</kbd> - `'/Applications/Android Studio.app'`<br/><br/>**Default Value:** none|
| <kbd>sandboxDirectory</kbd> - The path of sandbox directory that is used for running IDE with developing plugin.|**Acceptable Values:** <br/><kbd>path</kbd> - `'${project.rootDir}/.sandbox'` <br/><br/>**Default Value:** <kbd>'${project.buildDir}/idea-sandbox'</kbd>|

#### Infrastructure Properties
| Attributes | Values | 
| :------------- | :--------- | 
| <kbd>intellijRepo</kbd>, <kbd>pluginsRepo</kbd>, <kbd>jreRepo</kbd> - Urls of repositories for downloading IDE distributions, plugin dependencies and JetBrains Java Runtime. <br/><br/>|**Acceptable Values:** <br/><kbd>url</kbd><br/><br/>**Default Value:** <kbd>jetbrains.com/intellij-repository</kbd>, <kbd>plugins.jetbrains.com/maven</kbd>, <kbd>jetbrains.bintray.com/intellij-jdk</kbd>|
| <kbd>downloadSources</kbd> - Should plugin download IntelliJ sources while initializing Gradle build? <br/><br/>**Notes:**    <ul>        <li>Since sources are not needed while testing on CI, you can set it to `false` for a particular environment.</li>    </ul>|**Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd> if `CI` environment variable is not set|
| <kbd>ideaDependencyCachePath</kbd> - The absolute path to the local directory that should be used for storing IDE distributions. <br/><br/>**Notes:**    <ul>        <li>Empty value means the Gradle cache directory will be used.</li>    </ul>|**Acceptable Values:** <br/><kbd>path</kbd> - `'<example>'`<br/><br/>**Default Value:** none|


### Running DSL

`RunIde` tasks (both `runIde` and `buildSearchableOptions`) extend [JavaExec](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html) Gradle task,
all configuration attributes of `JavaExec` task can be used in `RunIde` as well.

In addition to that, following attributes may be used to customize IDE running:

| **Attributes**              | **Default Value**  |
| :-------------------------- | :----------------- |
| <kbd>jbrVersion</kbd> JetBrains Java runtime version to use when running the IDE with the plugin. | **Acceptable Values:** <kbd>String</kbd> - E.g. `'8u112b752.4'`, `'8u202b1483.24'`, or `'11_0_2b159'`. Prefixes `jbrex`, `jbrx` or `jbr` are allowed.<br/><br/>All JetBrains Java versions are available at [BinTray](https://bintray.com/jetbrains/intellij-jbr/).<br/><br/>**Default Value:** <kdb>null</kdb> for IDE &lt; 2017.3, <kdb>builtin java version</kdb>  for IDE &gt;= 2017.3 |
| <kbd>ideaDirectory</kbd> Path to IDE distribution that will be used to run the IDE with the plugin. | path to IDE-dependency |
| <kbd>configDirectory</kbd> Path to configuration directory. | <kbd>${intellij.sandboxDirectory}/config</kbd> |
| <kbd>pluginsDirectory</kbd> Path to plugins directory. | <kbd>${intellij.sandboxDirectory}/plugins</kbd> |
| <kbd>systemDirectory</kbd> Path to indexes directory. | <kbd>${intellij.sandboxDirectory}/system</kbd> |

### Patching DSL
The following attributes are a part of the Patching DSL <kbd>patchPluginXml { ... }</kbd> in which allows Gradle to patch specific attributes in a set of `plugin.xml` files.

| **Attributes**            | **Default Value** |
| :------------------------ |  :---------------- |
| <kbd>version</kbd> is a value for the `<version>` tag.                                | <kbd>project.version</kbd> |
| <kbd>sinceBuild</kbd> is for the `since-build` attribute of the `<idea-version>` tag. | <kbd>intellij.version</kbd> in `Branch.Build.Fix` format |
| <kbd>untilBuild</kbd> is for the `until-build` attribute of the `<idea-version>` tag. | <kbd>intellij.version</kbd> in `Branch.Build.*` format |
| <kbd>pluginDescription</kbd> is for the `<description>` tag.                          | none |
| <kbd>changeNotes</kbd> is for the `<change-notes>` tag.                               | none |
| <kbd>pluginXmlFiles</kbd> is a collection of xml files to patch.                      | All `plugin.xml` files with `<idea-plugin>` |
| <kbd>destinationDir</kbd> is a directory to store patched xml files.                  | <kbd>'${project.buildDir}/patchedPluginXmlFiles'</kbd> |

### Publishing DSL
The following attributes are a part of the Publishing DSL <kbd>publishPlugin { ... }</kbd> in which allows Gradle to upload a working plugin to the JetBrains Plugin Repository. Note that you need to upload the plugin to the repository at least once manually (to specify options like the license, repository URL etc.) before uploads through Gradle can be used.

See the instruction on how to generate authentication token: https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html

See [Publishing Plugins with Gradle](https://www.jetbrains.org/intellij/sdk/docs/tutorials/build_system/deployment.html) tutorial for step-by-step instructions.

| **Attributes**              | **Default Value**  |
| :-------------------------- | :----------------- |
| <kbd>token</kbd> Authentication token. | none |
| <kbd>channels</kbd> List of channel names to upload plugin to.  | <kbd>[default]</kbd> |
| <kbd>host</kbd>  URL host of a plugin repository.               | <kbd>https://plugins.jetbrains.com</kbd> |
| <kbd>distributionFile</kbd> Jar or Zip file of plugin to upload. | output of `buildPlugin` task |

### Instrumenting DSL
The following attributes help you to tune instrumenting behaviour in <kbd>instrumentCode { ... }</kbd> block.

| **Attributes**            | **Default Value** |
| :------------------------ |  :---------------- |
| <kbd>compilerVersion</kbd> is a version of instrumenting compiler. It's used for non-IDEA plugins (e.g. CLion or Rider). | <kbd>Build number of the IDE dependency</kbd> |

### build.gradle

```groovy
plugins {
  id "org.jetbrains.intellij" version "0.4.13"
}

intellij {
  version 'IC-2018.3'
  plugins = ['coverage', 'org.intellij.plugins.markdown:8.5.0.20160208']
  pluginName 'MyPlugin'

}
publishPlugin {
  token 'ssdfhasdfASDaq23jhnasdkjh'
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
  - Uses a Kotlin version not bundled with IntelliJ IDEA
- [Mainframer Integration](https://github.com/elpassion/mainframer-intellij-plugin)
	- Uses the Gradle Kotlin DSL
	- Fully written in Kotlin
	- Uses RxJava
- [Unity 3D plugin](https://github.com/JetBrains/resharper-unity) for JetBrains Rider
- [AEM Tools plugin](https://github.com/aemtools/aemtools) for Adobe Experience Manager integration
	- Uses the Gradle Kotlin DSL
	- Fully written in Kotlin
	- Uses template language
- [F# plugin](https://github.com/JetBrains/fsharp-support/tree/master/rider-fsharp) for JetBrains Rider
	- Uses the Gradle Kotlin DSL
- [Intellij Rainbow Brackets](https://github.com/izhangzhihao/intellij-rainbow-brackets)
	- Fully written in Kotlin
	- Uses other IntelliJ IDEA plugins as test dependencies
	- Circle CI configuration file & Travis CI configuration file
	- Gradle task to verify plugin compatibility cross IntelliJ Platform versions
	- Auto submit anonymous feedback as github issues

# Contributing

Contributing tips:

You can debug the source code of gradle-intellij-plugin (e.g. put breakpoints there) if you add a reference to your local copy into `settings.gradle` of your IntelliJ plugin:

```groovy
includeBuild '/path/to/gradle-intellij-plugin'
```

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

