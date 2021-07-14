<a name="documentr_top"></a>[![official JetBrains project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) [![Build Status](https://api.cirrus-ci.com/github/JetBrains/gradle-intellij-plugin.svg)](https://cirrus-ci.com/github/JetBrains/gradle-intellij-plugin)
 [![Twitter Follow](https://img.shields.io/twitter/follow/JBPlatform?style=flat)](https://twitter.com/JBPlatform/)
 [![Gradle Plugin Release](https://img.shields.io/badge/gradle%20plugin-1.1.3-blue.svg)](https://plugins.gradle.org/plugin/org.jetbrains.intellij) [![GitHub Release](https://img.shields.io/github/release/jetbrains/gradle-intellij-plugin.svg)](https://github.com/jetbrains/gradle-intellij-plugin/releases) 

<img src="./.github/readme/gradle-intellij-plugin.png" alt="Gradle IntelliJ Plugin"/>

# gradle-intellij-plugin

<h4><a id="the-latest-version" class="anchor" aria-hidden="true" href="#the-latest-version"><svg class="octicon octicon-link" viewBox="0 0 16 16" version="1.1" width="16" height="16" aria-hidden="true"><path fill-rule="evenodd" d="M4 9h1v1H4c-1.5 0-3-1.69-3-3.5S2.55 3 4 3h4c1.45 0 3 1.69 3 3.5 0 1.41-.91 2.72-2 3.25V8.59c.58-.45 1-1.27 1-2.09C10 5.22 8.98 4 8 4H4c-.98 0-2 1.22-2 2.5S3 9 4 9zm9-3h-1v1h1c1 0 2 1.22 2 2.5S13.98 12 13 12H9c-.98 0-2-1.22-2-2.5 0-.83.42-1.64 1-2.09V6.25c-1.09.53-2 1.84-2 3.25C6 11.31 7.55 13 9 13h4c1.45 0 3-1.69 3-3.5S14.5 6 13 6z"></path></svg></a>The latest version is 1.1.3</h4>

> 
**This project requires Gradle 6.6 or newer**

When upgrading to 1.x version, please make sure to follow migration guide to adjust your existing build script: https://lp.jetbrains.com/gradle-intellij-plugin

This plugin allows you to build plugins for IntelliJ Platform using specified IntelliJ SDK and bundled/3rd-party plugins.

The plugin adds extra IntelliJ-specific dependencies, patches `processResources` tasks to fill some tags 
(name, version) in `plugin.xml` with appropriate values, patches compile tasks to instrument code with 
nullability assertions and forms classes made with IntelliJ GUI Designer and provides some build steps which might be
helpful while developing plugins for IntelliJ platform.



# Getting started

> **TIP** Create new plugins with a preconfigured project scaffold and CI using
> [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).

Here is [the manual](https://plugins.jetbrains.com/docs/intellij/gradle-prerequisites.html) on how to start developing plugins for the IntelliJ Platform using Gradle.

Also, please take a look at [the FAQ](../../blob/master/FAQ.md).

# Usage
 
## Gradle 

```groovy
plugins {
  id "org.jetbrains.intellij" version "1.1.3"
}
```
 
### Snapshot version

<summary>To get the latest features, use the snapshot version of the plugin</summary>

```groovy
plugins {
  id "org.jetbrains.intellij" version "1.2-SNAPSHOT"
}
```

And define the snapshots repository in your `settings.gradle` file


```groovy
pluginManagement {
    repositories {
        maven {
            url 'https://oss.sonatype.org/content/repositories/snapshots/'
        }
        gradlePluginPortal()
    }
}
```

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
| <kbd>version</kbd> - The version of the IntelliJ Platform IDE that will be used to build the plugin. <br/><br/>Please see [Plugin Compatibility](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html) in SDK docs for more details.<br/><br/>**Notes:**    <ul>        <li>Value may have `IC-`, `IU-`, `CL-`, `PY-`, `PC-`, `RD-`, `GO-` or `JPS-` prefix in order to define IDE distribution type.</li>        <li>`intellij.version` and `intellij.localPath` should not be specified at the same time.</li>    </ul>|**Acceptable Values:**    <ul>        <li><kbd>version #</kbd><br/>`'2017.2.5'` or `'IC-2017.2.5'` </li>        <li><kbd>build #</kbd><br/>`'172.4343'` or `'IU-172.4343'` </li>        <li><kbd>'LATEST-EAP-SNAPSHOT'</kbd></li>    </ul><br/><br/>All available JetBrains IDEs versions can be found at [IntelliJ Artifacts](https://plugins.jetbrains.com/docs/intellij/intellij-artifacts.html) page.<br/><br/>**Default Value:** <kbd>'LATEST-EAP-SNAPSHOT'</kbd>|
| <kbd>type</kbd> - The type of IDE distribution.|**Acceptable Values:**    <ul>        <li><kbd>'IC'</kbd> - IntelliJ IDEA Community Edition. </li>        <li><kbd>'IU'</kbd> - IntelliJ IDEA Ultimate Edition. </li>        <li><kbd>'CL'</kbd> - CLion. </li>        <li><kbd>'PY'</kbd> - PyCharm Professional Edition. </li>        <li><kbd>'PC'</kbd> - PyCharm Community Edition. </li>        <li><kbd>'RD'</kbd> - Rider.</li>        <li><kbd>'GO'</kbd> - GoLand.</li>        <li><kbd>'JPS'</kbd> - JPS-only. </li>    </ul>**Default Value:** <kbd>'IC'</kbd>|
| <kbd>localPath</kbd> - The path to locally installed IDE distribution that should be used as a dependency. <br/><br/>**Notes:**    <ul>        <li>`intellij.version` and `intellij.localPath` should not be specified at the same time.</li>    </ul>|**Acceptable Values:** <br/><kbd>path</kbd> - `'/Applications/IntelliJIDEA.app'`</br></br>**Default Value:** <kbd>null</kbd>|
| <kbd>plugins</kbd> - The list of bundled IDE plugins and plugins from the [JetBrains Plugin Repository](https://plugins.jetbrains.com/). <br/><br/>Please see [Plugin Dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html) in SDK docs for more details.<br/><br/>**Notes:**    <ul>        <li>For plugins from the JetBrains Plugin Repository use format `pluginId:version`.</li>        <li>For bundled plugins version should be omitted: e.g. `org.intellij.groovy` for `IDEA/plugins/Groovy` plugin.</li>        <li>For sub-projects use project reference `project(':subproject')`.</li>        <li>If you need to refer plugin's classes from your project, you also have to define a dependency in your `plugin.xml`.</li>    </ul>|**Acceptable Values:**    <ol>        <li><kbd>org.plugin.id:version[@channel]</kbd><br/>`'org.intellij.plugins.markdown:8.5.0', 'org.intellij.scala:2017.2.638@nightly'`</li>        <li><kbd>bundledPluginName</kbd><br/>`'android', 'Groovy'`</li>        <li><kbd>project(':projectName')</kbd><br/>`project(':plugin-subproject')`</li>    </ol>**Default Value\:** none|

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
| <kbd>sandboxDir</kbd> - The path of sandbox directory that is used for running IDE with developing plugin.|**Acceptable Values:** <br/><kbd>path</kbd> - `'${project.rootDir}/.sandbox'` <br/><br/>**Default Value:** <kbd>'${project.buildDir}/idea-sandbox'</kbd>|

#### Infrastructure Properties
| Attributes | Values | 
| :------------- | :--------- | 
| <kbd>intellijRepository</kbd>, <kbd>jreRepository</kbd> - Urls of repositories for downloading IDE distributions and JetBrains Java Runtime. <br/><br/>|**Acceptable Values:** <br/><kbd>url</kbd><br/><br/>**Default Value:** <kbd>https://jetbrains.com/intellij-repository</kbd>, <kbd>https://cache-redirector.jetbrains.com/intellij-jbr</kbd>|
| <kbd>pluginsRepositories { ... }</kbd> - Configure repositories for downloading plugin dependencies. <br/><br/>|**Configuration:** <br/><kbd>marketplace()</kbd> - use Maven repository with plugins listed in the JetBrains marketplace<br/><kbd>maven(repositoryUrl)</kbd> - use custom Maven repository with plugins<br/><kbd>maven { repositoryUrl }</kbd> - use custom Maven repository with plugins where you can configure additional parameters (credentials, authentication and etc.)<br/><kbd>custom(pluginsXmlUrl)</kbd> - use [custom plugin repository](https://www.jetbrains.com/help/idea/managing-plugins.html) <br/><br/>**Default Configuration:** <kbd>pluginsRepositories { marketplace() }</kbd>|
| <kbd>downloadSources</kbd> - Should plugin download IntelliJ sources while initializing Gradle build? <br/><br/>**Notes:**    <ul>        <li>Since sources are not needed while testing on CI, you can set it to `false` for a particular environment.</li>    </ul>|**Acceptable Values:** <kbd>true</kbd> <kbd>false</kbd><br/><br/>**Default Value:** <kbd>true</kbd> if `CI` environment variable is not set|
| <kbd>ideaDependencyCachePath</kbd> - The absolute path to the local directory that should be used for storing IDE distributions. <br/><br/>**Notes:**    <ul>        <li>Empty value means the Gradle cache directory will be used.</li>    </ul>|**Acceptable Values:** <br/><kbd>path</kbd> - `'<example>'`<br/><br/>**Default Value:** none|


### Running DSL

`RunIde` tasks (both `runIde` and `buildSearchableOptions`) extend [JavaExec](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html) Gradle task,
all configuration attributes of `JavaExec` task can be used in `RunIde` as well.

In addition to that, following attributes may be used to customize IDE running:

| **Attributes**              | **Default Value**  |
| :-------------------------- | :----------------- |
| <kbd>jbrVersion</kbd> JetBrains Java runtime version to use when running the IDE with the plugin. | **Acceptable Values:** <kbd>String</kbd> - E.g. `'8u112b752.4'`, `'8u202b1483.24'`, or `'11_0_2b159'`. Prefixes `jbrex`, `jbrx` or `jbr` are allowed.<br/><br/>All JetBrains Java versions are available at [JetBrains Space Packages](https://cache-redirector.jetbrains.com/intellij-jbr/).<br/><br/>**Default Value:** <kdb>null</kdb> for IDE &lt; 2017.3, <kdb>builtin java version</kdb>  for IDE &gt;= 2017.3 |
| <kbd>ideDir</kbd> Path to IDE distribution that will be used to run the IDE with the plugin. | path to IDE-dependency |
| <kbd>configDir</kbd> Path to configuration directory. | <kbd>${intellij.sandboxDir}/config</kbd> |
| <kbd>pluginsDir</kbd> Path to plugins directory. | <kbd>${intellij.sandboxDir}/plugins</kbd> |
| <kbd>systemDir</kbd> Path to indexes directory. | <kbd>${intellij.sandboxDir}/system</kbd> |
| <kbd>autoReloadPlugins</kbd> Enable/disable [auto-reload](https://plugins.jetbrains.com/docs/intellij/ide-development-instance.html#enabling-auto-reload) of dynamic plugins. | <kbd>true</kbd> for IDE >= 2020.2 |

### Patching DSL
The following attributes are a part of the Patching DSL <kbd>patchPluginXml { ... }</kbd> in which allows Gradle to patch specific attributes in a set of `plugin.xml` files.

> **TIP** To maintain and generate an up-to-date changelog, try using [Gradle Changelog Plugin](https://github.com/JetBrains/gradle-changelog-plugin).

| **Attributes**            | **Default Value** |
| :------------------------ |  :---------------- |
| <kbd>version</kbd> is a value for the `<version>` tag.                                | <kbd>project.version</kbd> |
| <kbd>sinceBuild</kbd> is for the `since-build` attribute of the `<idea-version>` tag. | <kbd>intellij.version</kbd> in `Branch.Build.Fix` format |
| <kbd>untilBuild</kbd> is for the `until-build` attribute of the `<idea-version>` tag. | <kbd>intellij.version</kbd> in `Branch.Build.*` format |
| <kbd>pluginDescription</kbd> is for the `<description>` tag.                          | none |
| <kbd>changeNotes</kbd> is for the `<change-notes>` tag.                               | none |
| <kbd>pluginXmlFiles</kbd> is a collection of xml files to patch.                      | All `plugin.xml` files with `<idea-plugin>` |
| <kbd>destinationDir</kbd> is a directory to store patched xml files.                  | <kbd>'${project.buildDir}/patchedPluginXmlFiles'</kbd> |

### Plugin Verifier DSL
[IntelliJ Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) integration task allows to check the binary compatibility of the built plugin against the specified [IntelliJ IDE builds](https://plugins.jetbrains.com/docs/intellij/api-changes-list.html).

Plugin Verifier DSL `runPluginVerifier { ... }` allows to define the list of IDEs used for the verification, as well as explicit tool version and any of the available [options](https://github.com/JetBrains/intellij-plugin-verifier#common-options) by proxifying them to the Verifier CLI.

> **TIP** For more details, examples or issues reporting, go to the [IntelliJ Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) repository.

| **Attributes**                                                                                                                                                                                  | **Default Value**                                                                                                                              |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| <kbd>ideVersions</kbd> - IDEs to check, in `intellij.version` format, i.e.: `["IC-2019.3.5", "PS-2019.3.2"]`. Check the available build versions on https://jb.gg/intellij-platform-builds-list | none                                                                                                                                           |
| <kbd>verifierVersion</kbd> - IntelliJ Plugin Verifier version, by default uses the latest available. It's recommended to use always the latest version.                                         | <kbd>latest</kbd>                                                                                                                              |
| <kbd>verifierPath</kbd> - IntelliJ Plugin Verifier local path to the pre-downloaded jar file. If set, `verifierVersion` is ignored.                                                             | none                                                                                                                                           |
| <kbd>localPaths</kbd> - A list of the paths to locally installed IDE distributions that should be used for verification in addition to those specified in `ideVersions`.                        | <kbd>[]</kbd>                                                                                                                                  |
| <kbd>distributionFile</kbd> - Jar or Zip file of plugin to verify.                                                                                                                              | output of `buildPlugin` task                                                                                                                   |
| <kbd>failureLevel</kbd> - Defines the verification level at which task should fail. Can be set as `FailureLevel` enum or `EnumSet<FailureLevel>`.                                               | <kbd>FailureLevel.INVALID_PLUGIN</kbd>                                                                                                         |
| <kbd>verificationReportsDir</kbd> - The path to directory where verification reports will be saved.                                                                                             | <kbd>${project.buildDir}/reports/pluginVerifier</kbd>                                                                                          |
| <kbd>downloadDir</kbd> - The path to directory where IDEs used for the verification will be downloaded.                                                                                         | `System.getProperty("plugin.verifier.home.dir")/ides` or `System.getProperty("user.home")/.pluginVerifier/ides` or system temporary directory. |
| <kbd>jbrVersion</kbd> - JBR version used by the Verifier.                                                                                                                                       | none                                                                                                                                           |
| <kbd>runtimeDir</kbd> - The path to directory containing Java runtime, overrides JBR.                                                                                                           | none                                                                                                                                           |
| <kbd>externalPrefixes</kbd> - The prefixes of classes from the external libraries.                                                                                                              | none                                                                                                                                           |
| <kbd>teamCityOutputFormat</kbd> - Specify this flag if you want to print the TeamCity compatible output on stdout.                                                                              | none                                                                                                                                           |
| <kbd>subsystemsToCheck</kbd> - Specifies which subsystems of IDE should be checked. Available options: `all` (default), `android-only`, `without-android`.                                      | none                                                                                                                                           |

> **TIP** To run Plugin Verifier in [`-offline mode`](https://github.com/JetBrains/intellij-plugin-verifier/pull/58), set the Gradle [`offline` start parameter](https://docs.gradle.org/current/javadoc/org/gradle/StartParameter.html#setOffline-boolean-).

### Plugin Signing
To sign the plugin before publishing to the JetBrains Marketplace with the `signPlugin` task, it is required to provide a certificate chain and a private key with its password using `signPlugin { ... }` Plugin Signing DSL.

As soon as `privateKey` and `certificateChain` properties are specified, task will be executed automatically right before the `publishPlugin` task.

| **Attributes**              | **Default Value**  |
| :-------------------------- | :----------------- |
| <kbd>certificateChain</kbd> A string containing X509 certificates. | none |
| <kbd>privateKey</kbd> Encoded private key in PEM format. | none |
| <kbd>password</kbd> Password required to decrypt private key. | none |

### Publishing DSL
The following attributes are a part of the Publishing DSL <kbd>publishPlugin { ... }</kbd> in which allows Gradle to upload a working plugin to the JetBrains Plugin Repository. Note that you need to upload the plugin to the repository at least once manually (to specify options like the license, repository URL etc.) before uploads through Gradle can be used.

See the instruction on how to generate authentication token: https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html

See [Publishing Plugins with Gradle](https://plugins.jetbrains.com/docs/intellij/deployment.html) tutorial for step-by-step instructions.

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

# Examples

Marketplace platform provides the [IntelliJ Platform Explorer](https://jb.gg/ipe) – a search tool for browsing Extension Points inside existing implementations of open-source IntelliJ Platform plugins.

One of its features is the possibility of filtering the plugins by those that utilize [Gradle](https://jb.gg/ipe?buildSystem=gradle) or [Gradle KTS](https://jb.gg/ipe?buildSystem=gradle_kts) build scripts.

As examples of using this plugin, you can also check out following projects:

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
	- Auto subbmit anonymous feedback as github issues
- [Requirements](https://github.com/meanmail-dev/requirements)
	- Uses the Gradle Kotlin DSL
	- Fully written in Kotlin
	- Uses other IntelliJ IDEA plugins as test dependencies
	- Uses Grammar Kit
	- Uses a Kotlin version not bundled with IntelliJ IDEA
# Contributing

Contributing tips:

You can debug the source code of gradle-intellij-plugin (e.g. put breakpoints there) if you add a reference to your local copy into `settings.gradle` of your IntelliJ plugin:

```groovy
includeBuild '/path/to/gradle-intellij-plugin'
```

# License


```
Copyright 2021 org.jetbrains.intellij.plugins

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

