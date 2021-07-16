# Changelog

## next

- Fix `prepareTestingSandbox` not running when test task is executed [#745](../../issues/745) by @abrooksv
- Move signPlugin file name creation to lazy [#742](../../issues/742) by @brian-mcnamara

## 1.1.3

- Fixed dependency on `JavaScript` plugin [#674](../../issues/674)
- Fixed `releaseType` resolving for Rider versions in `-EAP#-SNAPSHOT` format.
- `runPluginVerifier`: verify required Java 11 environment for Plugin Verifier `1.260+`
- `pluginVerifier` – remove support for old versions `< 1.255` hosted on Bintray
- Fixed tests configuration – 'Config Directory' does not exist exception

## 1.1.2

- Use Gradle `ArchiveOperations` in `extractArchive` utils method [#681](../../issues/681)
- Set minimal supported Gradle version to 6.6
- Use JDOM for altering `updates.xml` in `PrepareSandboxTask` to keep existing content
- Fixed incorrect output path of `JarSearchableOptionsTask` causing also duplicate entry exception [#678](../../issues/678)
- Fixed incorrect plugin download URL for custom repositories [#688](../../issues/688)
- Make `DownloadRobotServerPluginTask` pointing to the latest Robot Server Plugin available
- Support Maven closure in `PluginsRepositories` block
- `BuildSearchableOptionsTask` fails on macOS when resolving `javaHome` #[#696](../../issues/696)
- `PrepareSandboxTask` doesn't depend on `JavaPlugin` dependencies #[#451](../../issues/451)
- Remove `IntelliJPluginExtension.pluginsRepositories(block: Closure<Any>)` due to `ConfigureUtil` deprecation and a lack of typed parameters
- Remove usage of deprecated methods and classes introduced in Gradle 7.1 #[#700](../../issues/700)

## 1.0.0

- Breaking changes guide: https://lp.jetbrains.com/gradle-intellij-plugin
- Plugin Signing integration
- Lazy Configuration support
- Configuration Cache support
- Task Configuration Avoidance support
- better CI (GitHub Actions, Qodana, Dependabot)
- Rewritten in Kotlin
- property names cleanup (`*Repo` to `*Repository`, `*Directory` to `*Dir` – for the sake of consistency with Gradle)
- Stepping away from Bintray and JCenter

## 0.7.3

- migrate from bintray [#594](../../issues/594)
- exclude kotlin-reflect and kotlin-text from the runtime if kotlin is used in plugin [#585](../../issues/585)
- respect overridden `build` directory [#602](../../issues/602)
- store cache of plugins from different custom repositories in different directories [#579](../../issues/579)
- rename dependency jars with the same name [#497](../../issues/497)

## 0.7.2

- fix classpath for IDE without `ant` inside distribution
- fix resolving the OS architecture

## 0.7.1

- fix classpath for IDE 2020.2 [#601](../../issues/601)

## 0.7.0

- support GoLand as an SDK
- fix javac2 dependency for project with implicit IntelliJ version [#592](../../issues/592)
- fix using query parameters in custom repository urls [#589](../../issues/589)
- support downloading JBR for aarch64 [#600](../../issues/600)
- added ant dependencies to testing classpath
- fix JBR resolving after removing JavaFX from JBR in IDEA 2021.1 [#599](../../issues/599)

## 0.6.5

- fixed not found classes from plugin dependencies in tests [#570](../../issues/570)

## 0.6.4

- runPluginVerifier: integrate Plugin Verifier offline mode with Gradle `offline` start parameter
- runPluginVerifier: introduce `verifierPath` property
- support for Rider for Unreal Engine as an SDK

## 0.6.3

- fixed loading dependencies of builtin plugin [#542](../../issues/542)
- fixed loading file templates from plugins [#554](../../issues/554)
- yet another fix for class-loading in tests for IntelliJ Platform 203 and higher [#561](../../issues/561)

## 0.6.2

- runPluginVerifier: make ideVersions property mandatory
- runPluginVerifier: better handling of the exception produced by DownloadAction [#553](../../issues/553)
- runPluginVerifier: provide URL for verifying the available IDE versions [#553](../../issues/553)
- runPluginVerifier: fix java.nio.file.FileAlreadyExistsException as ERROR in logs [#552](../../issues/552)
- add prepareTestingSandbox as an input to tests

## 0.6.1

- runPluginVerifier: allow specifying `ideVersions` as comma-separated String
- runPluginVerifier: specifying EAP build number leads to IllegalArgumentException
- runPluginVerifier: fix for ArrayIndexOutOfBoundsException when destructuring ideVersion.split

## 0.6.0

- Introduced runPluginVerifier task that runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IntelliJ IDE builds. 

## 0.5.1

- fix class-loading in tests for IntelliJ Platform >= 203 

## 0.5.0

- do not download dependencies during configuration phase [#123](../../issues/123)
- support multiple plugin repositories
- support enterprise plugin repositories [#15](../../issues/15)

## 0.4.26

- fix plugin-repository-rest-client dependency

## 0.4.25

- fix plugin-repository-rest-client dependency

## 0.4.24

- fix plugin-repository-rest-client dependency

## 0.4.23

- fix compatibility issue with Kotlin 1.4 serialization [#532](../../issues/532)

## 0.4.22

- add option to disable auto-reload of dynamic plugins
- documentation improvements

## 0.4.21

- fix adding serchable options to the distribution for Gradle > 5.1 [#487](../../issues/487)

## 0.4.20

- fixed caching builtin plugins data
- add annotations-19.0.0 to compile classpath by default 
- fix setting plugin name for Gradle 5.1..5.3 [#481](../../issues/481)

## 0.4.19

- Use builtin JBR from alternativeIdePath IDE [#358](../../issues/358)
- Enable dependencies for builtin plugins automatically [#474](../../issues/474)
- Allow referring builtin plugins by their ids rather than directory name [IDEA-233841](https://youtrack.jetbrains.com/issue/IDEA-233841)
- Require 4.9 Gradle version, dropped deprecated stuff
- Do not add junit.jar into classpath, it may clash with junit-4.jar on certain JDKs

## 0.4.18

- Introduced `runIdeForUiTests` task [#466](../../issues/466)
- Fix unpacking JBR with JCEF on Mac [#468](../../issues/468)
- Publish plugin security update [#472](../../issues/472)

## 0.4.17

- Fix platform prefix for DataGrip [#458](../../issues/458)
- Enable plugin auto-reloading by default
- Upgrade plugins repository client
- Use new methods for Gradle 5.1 and higher [#464](../../issues/464)
- Support JBR with JCEF [#465](../../issues/465)

## 0.4.16

- Fix downloading JBR if temp directory and gradle chace are on the different partitions [#457](../../issues/457)
- Build searchable options task is marked as cacheable

## 0.4.15

- Fix uploading on Java 11 [#448](../../issues/448)
- Fix instrumentation when localPath is set [#443](../../issues/443)

## 0.4.14

- Support for Gradle 6.0
- Deprecated `runIde.ideaDirectory`. `runIde.ideDirectory` should be used instead

## 0.4.13

- Removed `intellij.useProductionClassLoaderInTests` option as we found another way to fix loading plugins in tests in 2019.3 

## 0.4.12

- More structured logging
- Introduced `intellij.useProductionClassLoaderInTests` option to control how plugin is going to be loaded in tests 

## 0.4.11

- Fix setting archive name for Gradle 5.1 and higher [#436](../../issues/436)  
- Fix forms compilation for Rider and Python snapshot builds. Works for Rider-2019.3-SNAPSHOT and higher [#403](../../issues/403)   

## 0.4.10

- Upgrade download plugin [#418](../../issues/418)
- Simplify custom runIde task configuration [#401](../../issues/401)


## 0.4.9

- Graceful handling of 404 errors when publishing a new plugin [#389](../../issues/389)
- Support PyCharm as an SDK
- Fail if the plugin depends on Java plugin but doesn't declare it as dependency

## 0.4.8

- Gradle 5.4 compatibility
- Support for new JBR distributions layout
- Made buildSearchableOption task incremental

## 0.4.7

- add one more executable file in Rider SDK

## 0.4.6

- support Gradle 5.3 [#379](../../issues/379)
- fixed downloading JBR 8 for IDEAE 2018.3 and earlier

## 0.4.5

- support JBR 11 from the new JetBrains Runtime Reposiotry
- support running using JBR 11 [IDEA-208692](https://youtrack.jetbrains.com/issue/IDEA-208692)

## 0.4.4
- support the new bintray repository for JetBrains Runtime artifacts
- fixed downloading of old JBR  builds [#367](../../issues/367)
- fix instrumentation for local IDE instances [#369](../../issues/369)

## 0.4.3

- fixed downloading instrumentation dependencies for release versions
- fixed downloading renamed JetBrains Runtime artifacts

## 0.4.2

- fixed removing `config/` and `system/` on running `runIde` task [#359](../../issues/359)

## 0.4.1

- fixed plugin's sources attaching

## 0.4.0

- drop Gradle 2 support
- support for CLion as a building dependency [#342](../../issues/342)
- support token-based authentication while publishing plugins [#317](../../issues/317)
- add notification about patching particular tag values and attributes in plugin.xml [#284](../../issues/284)
- fix attaching sources to bundled plugins [#337](../../issues/337)
- fix verification message in case of default value of `description`-tag

## 0.3.12

- fixed resolving plugins from a custom channel [#320](../../issues/320)
- fixed building with Java 9

## 0.3.11

- ~~fixed resolving plugins from a custom channel~~
- fixed uploading plugins [#321](../../issues/321)
- fixed caching strategy for IDEA dependency [#318](../../issues/318)
 
## 0.3.10

- fixed dependency on local plugin files
- cache-redirector is used for downloading plugin dependencies [#301](../../issues/301) 

## 0.3.7

- fixed missing `tools.jar` on Mac [#312](../../issues/312)

## 0.3.6

- `runIde` task uses `tools.jar` from a JBRE java [#307](../../issues/307)

## 0.3.5

- Allow to override all system properties in RunIde task [#304](../../issues/304)
- Move to the new url to JBRE and Gradle distributions [#301](../../issues/301)
- Fixed an encoding while writing plugin.xml [#295](../../issues/295)

## 0.3.4

- Gradle 4.8 compatibility [#283](../../issues/283)

## 0.3.3

- fixed compiling JGoodies forms for IDEA version >= 182.* [#290](../../issues/290)

## 0.3.2

- use tools.jar from a java of `runIde` task [IDEA-192418](https://youtrack.jetbrains.com/issue/IDEA-192418)

## 0.3.1

- fix running for IDEA version < 2017.3 [#273](../../issues/273)

## 0.3.0

- added plugin verification task: `verifyPlugin`
- default values of `runIde` task are propagated to all RunIdeaTask-like tasks
- enhanced plugins resolution: better error messages for unresolved dependencies and fixes [#247](../../issues/247)
- check build number to decide whether the unzipped distribution can be reused (fixes [#234](../../issues/234))
- download JetBrains Java runtime and use it while running IDE (fixes [#192](../../issues/192))
- do not include plugin's jars recursively (fixes [#231](../../issues/231))
- allow to add custom Javac2.jar to `instrumentCode` task

## 0.2.20

- recognize new kotlin stdlib files as part of IDEA dependency

## 0.2.19

- Setup project plugin dependency for an already evaluated project (fixes [#238](../../issues/238))

## 0.2.18

- update default repository url
- support for running GoLand 

## 0.2.17

- fix compatibility with Gradle 4.0 new versions of Kotlin and Scala plugins (fixes [#221](../../issues/221) and [#222](../../issues/222))

## 0.2.16

- automatically set system properties for debugging Resharper

## 0.2.15

- restore scripts execution permissions in Rider distribution 

## 0.2.14

- support RD prefix for Rider
- avoid possible NPEs (fixes [#208](../../issues/208))

## 0.2.13

- Gradle 4.0 compatibility fixes

## 0.2.12

- upgrade plugin-repository-rest-client

## 0.2.11

- upgrade plugin-repository-rest-client

## 0.2.10

- upgrade plugin-services libraries to fix 'Invalid plugin type' exception while downloading plugins dependencies (fixes [#201](../../issues/201))
- prefer `compile` configuration for any plugins IDEA dependencies in tests (fixes [#202](../../issues/202))

## 0.2.9

- prefer `compile` configuration for bundled plugins IDEA dependencies in tests

## 0.2.8

- prefer `compile` configuration for IDEA dependencies in tests
- prefer `compileOnly` configuration for plugins dependencies in tests

## 0.2.7

- avoid exception due to adding duplicated configurations

## 0.2.6

- prefer `compileOnly` configuration for IDEA dependencies

## 0.2.5

- set `buildDir` as a default cache for IDE dependencies in case of Rider-plugin
- fix Kotlin instrumentation

## 0.2.4

- fixed attaching sources for IDEA Ultimate and bundled plugins

## 0.2.3

- fixed compilation for multi-module layout
 
## 0.2.2

- added `runIde` task. `runIdea` is deprecated now (fixes [#169](../../issues/169))
- fixed kotlin forms instrumentation (fixes [#171](../../issues/171))
- fixed filtering out all resources of dependent plugins (fixes [#172](../../issues/172))
- fixed intellij.systemProperties extension (fixes [#173](../../issues/173)) 

## 0.2.1

- added Rider support (fixes [#167](../../issues/167))
- fix unresolved builtin plugins on case-insensitive file systems

## 0.2.0

- result artifact format is changed: now it's always a ZIP archive even if plugin has no extra dependencies. *Note that this may change classloading (see [#170](../../issues/170))*
- added an ability to use local IDE installation for compiling
- result zip archive is added to `archives` configuration, built-in `assemble` task now builds the plugin distribution
- added JPS-type for intellij dependency (fixes [#106](../../issues/106))
- patchXml action is reimplemented, now it's possible to freely customize input files, destination directory, since/until builds, plugin description and version
- publishTask is reimplemented, now it's possible to set several channels to upload (fixes [#117](../../issues/117))
- reimplementation tasks also includes following improvements for all of them:
  - it's possible to reuse reimplemented tasks in client's code
  - it's allowed to run tasks without plugin.xml
  - tasks are configured before project evaluation, `project.afterEvaluate` is not require anymore
- fix incremental compiling after instrumenting code (fixes [#116](../../issues/116))
- added `intellij.ideaDependencyCachePath` option (fixes [#127](../../issues/127))
- `project()` reference can be used as a plugin-dependency (fixes [#17](../../issues/17))
- fix attaching sources of builtin plugins (fixes [#153](../../issues/153))

## 0.1.10

**Avoid using this version unless you have several plugin project which use the very same sandbox directory**

- Do not override plugins directory content (temporary fix of [#17](../../issues/17)) 

## 0.1.9

- Added default configuration to ivy-repositories (fixes [#114](../../issues/114))

## 0.1.6

- External plugin directories are placed in compile classpath so IDEA code insight is better for them now (fixes [#105](../../issues/105)) 

## 0.1.4

- Fix incremental compilation on changing `intellij.version` (fixes [#67](../../issues/67))

## 0.1.0

- Support external plugin dependencies

## 0.0.41

- Fix Kotlin forms instrumentation ([#73](../../issues/73))

## 0.0.39

- Allow to make single-build plugin distributions (fixes [#64](../../issues/64))

## 0.0.37

- Exclude kotlin dependencies if needed (fixes [#57](../../issues/57))

## 0.0.35

- Disable automatic updates check in debug IDEA (fixes [#46](../../issues/46))

## 0.0.34

- Support local IDE installation as a target application of `runIdea` task

## 0.0.33

- Attach community sources to ultimate IntelliJ artifact (fixes [#37](../../issues/37))
- New extension for passing system properties to `runIdea` task (fixes [#18](../../issues/18))

## 0.0.32

- Support compilation in IDEA 13.1 (fixes [#28](../../issues/28))

## 0.0.30

- Fixed broken `runIdea` task

## 0.0.29

- `cleanTest` task clean `system-test` and `config-test` directories (fixes [#13](../../issues/13))
- Do not override plugins which were installed in debug IDEA (fixes [#24](../../issues/24))

## 0.0.28

- `RunIdeaTask` is extensible (fixes [#23](../../issues/23))
- Fix xml parsing exception (fixes [#25](../../issues/25))

## 0.0.27

- Disabled custom class loader in tests (fixes [#21](../../issues/21))

## 0.0.25

- Do not patch version tag if `project.version` property is not specified (fixes [#11](../../issues/11))

## 0.0.21

- IntelliJ-specific jars are attached as compile dependency (fixes [#5](../../issues/5))

## 0.0.10

- Support for attaching IntelliJ sources in IDEA
