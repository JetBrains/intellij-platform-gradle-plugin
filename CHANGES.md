# Changelog

## next
### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

## [1.8.0]
### Added
- Add `sourceSets` output directories to the classpath of the `test` task.
- Synchronize `OpenedPackages` list with the [latest version](https://raw.githubusercontent.com/JetBrains/intellij-community/master/plugins/devkit/devkit-core/src/run/OpenedPackages.txt) available.
- Make PhpStorm (`PS` type) available for resolving as SDK.

### Changed
- Rearrange classpath to put `idea` and `ideaPlugins` dependencies in the right order.
- Rename plugin configurations to move injected dependencies to the end of the classpath. [#1060](../../../1060)

### Removed
- Remove the `DEPENDENCY_FIRST` resolution strategy set by default along with its `BuildFeature.USE_DEPENDENCY_FIRST_RESOLUTION_STRATEGY` flag.
- Remove setting of the `java.system.class.loader` property from tests configuration. 

### Fixed
- Exclude non-jar files from the classpath [#1009](../../issues/1009)
- Jacoco reports false 0% test coverage [#1065](../../issues/1065)
- Unable to load JUnit4 runner to calculate Ignored test cases [#1033](../../issues/1033)

## 1.7.0
### Added
- Automatically detect bundled sources in plugin dependency [#786](../../issues/786)
- Automatically detect plugin dependency sources provided in the IDE distribution [#207](../../issues/207)
- Throw an error when `intellij.version` is missing [#1010](../../issues/1004)
- Set `ResolutionStrategy.SortOrder.DEPENDENCY_FIRST` for `compileClasspath` and `testCompileClasspath` configurations [#656](../../issues/656)
- Added `useDependencyFirstResolutionStrategy` feature flag. See [Feature Flags](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#build-features).
- Ensure `classpath.index` is not bundled in the JAR file
- Warn about no settings provided by the plugin when running `buildSearchableOptions` and suggest [disabling the task](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-options). [#1024](../../issues/1024)
- Warn about paid plugin running `buildSearchableOptions` and suggest [disabling the task](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-options). [#1025](../../issues/1025)
- IDE dependencies are added to the `compileOnly` classpath for test fixtures if the `java-test-fixtures` plugin is applied [#1028](../../issues/1028)
- `classpathIndexCleanup` task is added to remove `classpath.index` files created by `PathClassLoader` [#1039](../../issues/1039)
- Improve Plugin Verifier error messages [#1040](../../issues/1040)
- Added `FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES` to the Plugin Verifier task
- Support for JetBrains Runtime 2022.2 directories layout [#1016](../../issues/1016)

### Changed
- Set minimum supported Gradle version from `6.7` to `6.7.1`
- Resolve dependencies using repositories in the following order: project custom repositories (if any), plugin custom repositories, common repositories (like Maven Central)
- Add executable flag for `Rider.Backend` native launchers in `IdeaDependencyManager#resetExecutablePermissions` [RIDER-59978](https://youtrack.jetbrains.com/issue/RIDER-59978)
- Remove Gradle dependencies constraints as transitive dependencies don't point to vulnerabilities anymore [#999](../../issues/999)

### Fixed
- Fixed broken instrumentation when custom sources directory is set [#1004](../../issues/1004)
- Fixed `java.nio.file.FileAlreadyExistsException` when instrumenting code [#998](../../issues/998)
- Fixed `Execution optimizations have been disabled for task ':jar' to ensure correctness` [#1000](../../issues/1000)
- Fixed JaCoCo `Can't add different class with same name` exception when using code instrumentation [#1020](../../issues/1020)
- Fixed failing instrumentation due to the `Class not found` exception [#1029](../../issues/1029)
- Fixed `'compilerClassPathFromMaven' doesn't have a configured value` when resolving `java-compiler-ant-tasks` [#1003](../../issues/1003)
- Fixed `NoClassDefFoundError` caused by the stale `classpath.index` created by the `PathClassLoader` [#1032](../../issues/1032)
- Fixed issue with not updated GUI form during the incremental build [#1044](../../issues/1044)]

## 1.6.0
### Added
- Added `BuildFeature` feature flags. See [Feature Flags](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#build-features).
- Added `--jbr-illegal-access` and `-XX:+IgnoreUnrecognizedVMOptions` flags for tasks based on `RunIdeBase` to support `2022.2` which runs on Java 17
- For JBR 17, `dcevm` is bundled by default. As a consequence, separated `dcevm` and `nomod` variants are no longer available.
- `instrumentCode` task – incremental instrumentation [#459](../../issues/459)
- Add `intellijRepository` to the beginning of the repositories list when resolving dependencies [#615](../../issues/615)
- Set `-Djdk.module.illegalAccess.silent=true` flag by default to mute `WARNING: An illegal reflective access operation has occurred`
- Add `java.system.class.loader=com.intellij.util.lang.PathClassLoader` system property for tests run on 221+
- Integration Tests: Cover `instrumentCode` task

### Changed
- Set minimal supported Gradle version from `6.6` to `6.7`
- Throw an exception instead of warning when both `intellij.localPath` and `intellij.version` are specified
- Publish sources and javadocs within the release [#810](../../issues/810)

### Fixed
- Fix for `getHeaderField("Location") must not be null` [#960](../../issues/960)
- `instrumentCode` task – changes in Kotlin code no longer rebuild the plugin [#959](../../issues/959)
- Could not resolve JBR for 222-EAP-SNAPSHOT [#997](../../issues/997)
- Kotlin-generated classes aren't packed into the plugin distribution [#978](../../issues/978)
- Fixed version parsing for `listProductsReleases` task which resulted in incorrect IDE releases versions [#995](../../issues/995)
- Release `plugin.xml` file after reading it [IDEA-291836](https://youtrack.jetbrains.com/issue/IDEA-291836)

## 1.5.3
- Updated dependencies marked as vulnerable
- Fixed code instrumentation disabling via `tasks.instrumentCode.enabled`
- `instrumentCode` task – limit the scope of the task to `sourceSets.main.java` [#459](../../issues/459)
- Introduce Gradle IntelliJ Plugin version check against the latest available

## 1.5.2
- Add `util_rt.jar` to the classpath of run-like tasks for `2022.1+` compatibility

## 1.5.1
- Make IDEA products releases cached hourly [#848](../../issues/848)
- Fixed `ListProductReleasesTask` to return only significant versions for Android Studio [#928](../../issues/928)

## 1.5.0
- Include Android Studio builds in the `ListProductsReleasesTask` results
- Fix compiler resolution for EAP versions [#906](../../issues/906)
- Initial Toolbox Enterprise integration [#913](../../issues/913)
- Make IDEA products releases cached daily [#848](../../issues/848)
- Fixed `ListProductsReleasesTask` to allow for empty `untilBuild` [#909](../../issues/909)
- Resolved closest lower java-compiler-ant-tasks version if provided isn't available [#910](../../issues/910)
- Fixed XML parsing with JAXB - drop intermediate JDOM Document

## 1.4.0
- Fixed JBR resolving for MacOSX M1
- Fixed compiler resolution for long build numbers [#883](../../issues/883)
- Build number fallback when `product-info.json` is missing [#880](../../issues/880)
- Consider `sinceBuild` and `untilBuild` properties of `ListProductsReleasesTask` in task caching [#891](../../issues/891)
- Introduce `jbrVariant` property next to the `jbrVersion` property in `runIde`, `runPluginVerifier`, `buildSearchableOptions`, and `runIdeForUiTest` tasks [#852](../../issues/852)
- Change log level of `JbrResolver.resolveRuntime` logs from `warn` to `debug` [#849](../../issues/849)
- Update runtime classpath for `221+`
- Fixed resolving Java Runtime for MacOSX [#895](../../issues/895)
- ProductInfo: parse custom properties in `product-info.json` [#897](../../issues/897)
- Make `IntelliJInstrumentCodeTask` incremental

## 1.3.1
- Fixed execution bit filter when extracting Rider [RIDER-72922](https://youtrack.jetbrains.com/issue/RIDER-72922)
- Revert `org.jetbrains.intellij:blockmap` dependency to the latest `1.0.5` version
- Avoid querying `intellij.version` when `intellij.localPath` is set
- Fixed `BuildSearchableOptionsTask` for `2022.1+` version of IDE [RIDER-73264](https://youtrack.jetbrains.com/issue/RIDER-73264)
- `ListProductsReleasesTask`: rely on the `patchPluginXml.sinceBuild`/`patchPluginXml.untilBuild` properties instead of `intellij.version`
- `ListProductsReleasesTask`: allow using IDE version along with build numbers

## 1.3.0
- IntelliJ Plugin Verifier allows for running against Android Studio (i.e. `AI-2021.2.1.4`)
- Make `intellij.version` property mandatory
- Move `intellij.ideaDependency` to the `SetupDependenciesTask.idea`
- Postpone the initial dependencies downloading to the `setupDependencies` task which is run in the `afterSync` phase or by individual tasks
- Provide build information within the `META-INF/MANIFEST.MF` file
- Resolve EAP version of the Java compiler for `LATEST-EAP-SNAPSHOT`
- Allow for using `dcevm`, `fd`, and `nomod` variants of JBR [#818](../../issues/818)
- `ListProductsReleasesTask.updatesPath` changed to `ListProductsReleasesTask.updatePaths`
- `ListProductsReleasesTask.includeEAP` changed to `ListProductsReleasesTask.releaseChannels`

## 1.2.1
- Respect `ideaDependencyCachePath` property [#794](../../issues/794)
- Fix for providing dependencies after project evaluation [#801](../../issues/801)
- Resolve EAP version of the Java compiler for local EAP IDE instances [#811](../../issues/811)
- Allow for passing an empty array for `runPluginVerifier.ideVersions` property [#809](../../issues/809)

## 1.2.0
- Fixed running tests on 2021.3 platform version
- Avoid downloading IDE dependency in configuration phase
- Deprecate `IntelliJPluginExtension.getIdeaDependency(project: Project)`
- Increase the default `runPluginVerifier.failureLevel` to `COMPATIBILITY_PROBLEMS`
- Introduce `listProductsReleases` task for listing the IDE releases matching given criteria
- Fixed resolving compiler classpath for the `instrumentCode` task when using `LATEST-EAP-SNAPSHOT` [#752](../../issues/752)
- Fixed resolving `idea.platform.prefix` [#772](../../issues/772)
- Fix for custom `buildDir` not used in some `instrumentCode` and `buildSearchableOptions` tasks [#793](../../issues/793)

## 1.1.6
- Fixed returned list of paths to IDEs downloaded for Plugin Verifier [#779](../../issues/779)

## 1.1.5
- Use target Kotlin API Version 1.3 [#750](../../issues/750)
- Migrate `SignPluginTask` to use the Marketplace ZIP Signer CLI
- Fixed resolving of built-in JetBrains Runtime (JBR) [#756](../../issues/756)

## 1.1.4
- Configuration cache enhancements
- Fix `prepareTestingSandbox` not running when test task is executed [#745](../../issues/745) by @abrooksv
- Move signPlugin file name creation to lazy [#742](../../issues/742) by @brian-mcnamara
- Better platform prefix resolving

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
- runPluginVerifier: fix for `ArrayIndexOutOfBoundsException` when destructuring `ideVersion.split`

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
- fix adding searchable options to the distribution for Gradle > 5.1 [#487](../../issues/487)

## 0.4.20
- fixed caching builtin plugins data
- add annotations-19.0.0 to compile classpath by default 
- fix setting plugin name for Gradle 5.1-5.3 [#481](../../issues/481)

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
- fixed downloading JBR 8 for IDEA 2018.3 and earlier

## 0.4.5
- support JBR 11 from the new JetBrains Runtime Repository
- support running using JBR 11 [IDEA-208692](https://youtrack.jetbrains.com/issue/IDEA-208692)

## 0.4.4
- support the new bintray repository for JetBrains Runtime artifacts
- fixed downloading of old JBR builds [#367](../../issues/367)
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
- allow adding custom Javac2.jar to `instrumentCode` task

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
- Allow making single-build plugin distributions (fixes [#64](../../issues/64))

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