# Overview

[![Join the chat at https://gitter.im/JetBrains/gradle-intellij-plugin](https://badges.gitter.im/JetBrains/gradle-intellij-plugin.svg)](https://gitter.im/JetBrains/gradle-intellij-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This plugin allows you to build plugins for IntelliJ platform using specific IntelliJ SDK and bundled plugins.

The plugin adds extra IntelliJ-specific dependencies, patches processResources tasks in order to fill some tags 
(name, version) in `plugin.xml` with appropriate values, patches compile tasks in order to instrument code with 
nullability assertions and forms classes made with IntelliJ GUI Designer and provides some build steps which might be
helpful while developing plugins for IntelliJ platform.

# Usage

## Gradle >= 2.1

```groovy
plugins {
  id "org.jetbrains.intellij" version "0.0.43"
}
```

## Gradle < 2.1

```groovy
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath 'gradle.plugin.org.jetbrains:gradle-intellij-plugin:0.0.43'
  }
}

apply plugin: 'org.jetbrains.intellij'
```

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

- `intellij.plugins` defines the list of bundled IDEA plugins that should be used as dependencies.<br/><br/> 
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
initializing gradle build. Since sources are not really needed while testing on CI you can set
it to `false` for particular environment.<br/><br/>
**Default value**: `true`

- `intellij.systemProperties` defines the map of system properties which will be passed to IDEA instance on
executing `runIdea` task and tests.<br/>
Also you can use `intellij.systemProperty(name, value)` method in order to set single system property.
<br/><br/>
**Default value**: `[]`

- `intellij.alternativeIdePath` – absolute path to locally installed JetBrains IDE.
It make sense to use this property if you want to test your plugin in WebStorm or any other non-IDEA JetBrains IDE.
Empty value means that the IDE that was used for compiling will be used for running/debugging as well.<br/><br/>
**Default value**: `<empty>`

#### Publishing plugin

- `intellij.publish.pluginId` defines plugin id at JetBrains plugin repository, you can find it in url of you plugin page there.
- `intellij.publish.username` your login at JetBrains plugin repository.
- `intellij.publish.password` your password at JetBrains plugin repository.
- `intellij.publish.channel` defines channel to upload, you may use any string here, empty string means default channel.
<br/><br/>
**Default value**: `<empty>`

#### Build steps

Plugin introduces following build steps

- `prepareSandbox` creates proper structure of plugin and fills sandbox directory with it
- `buildPlugin` assembles plugin and prepares zip archive for deployment
- `runIdea` executes IntelliJ IDEA instance with installed the plugin you're developing 
- `publishPlugin` uploads plugin distribution archive to http://plugins.jetbrains.com 

### build.gradle
```groovy
plugins {
  id "org.jetbrains.intellij" version "0.0.43"
}

intellij {
  version 'IC-14.1.4'
  plugins 'coverage'
  pluginName 'MyPlugin'

  publish {
    username 'zolotov'
    password 'password'
    pluginId '5047'
    channel 'nightly'
  } 
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
- [Android Drawable Importer plugin](https://github.com/winterDroid/android-drawable-importer-intellij-plugin)
- [Android Material Design Icon Generator plugin](https://github.com/konifar/android-material-design-icon-generator-plugin)
- [EmberJS plugin](https://github.com/Turbo87/intellij-emberjs)
- [GCloud plugin](https://github.com/GoogleCloudPlatform/gcloud-intellij)
- [HCL plugin](https://github.com/VladRassokhin/intellij-hcl)
- [Robot plugin](https://github.com/AmailP/robot-plugin)
- [TOML plugin](https://github.com/stuartcarnie/toml-plugin)
- [SQLDelight Android Studio Plugin](https://github.com/square/sqldelight/tree/master/sqldelight-studio-plugin)
- https://github.com/breandan/idear
- https://github.com/pedrovgs/AndroidWiFiADB
- https://github.com/SonarSource/sonar-intellij


# License
This plugin is available under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
