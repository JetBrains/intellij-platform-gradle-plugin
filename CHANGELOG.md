# Changelog

## [next]

### Added

- Introduce `intellijPlatformClasspath` configuration to allow retrieving the processed IntelliJ Platform and plugins dependencies. 

### Changed

- Improved build performance by pre-creating Ivy XML files in the extracted IDE location.
- Improved build performance by making the local Ivy repository first in the list and making it an exclusive Gradle repository.

### Fixed

- Fixed issue #1778 by removing a hash of the absolute artifact path appended to the end of the version string. That hash made artifact version different on different PCs and also breaks Gradle dependency locking.
- Add the missing `org.jetbrains.kotlin.platform.type=jvm` attribute to the `intellijPlatformRuntimeClasspath` configuration manually as it's not inherited from the `runtimeClasspath`.
- Fixed `Could not generate a decorated class for type PluginArtifactRepository.` when creating a custom plugin repository.
- #1779 Fixes compatibility with Gradle dependency verification. Previously it was failing with "Failed to create MD5 hash for file".

## [2.1.0]

### Added

- Added `PrepareSandboxTask.pluginName` for easier accessing of the plugin directory name
- Allow for using non-installer IDEs for plugin verification [#1715](../../issues/1715)
- Added `bundledModule()` dependency extension helpers
- Detect and warn about the `kotlinx-coroutines` library in transitive dependencies
- Introduce caching when creating dependencies 

### Changed

- Add IntelliJ Platform v2 product modules to the test classpath
- Invalidate local dependency artifacts XML files running

### Fixed

- Fixed caching for `IntelliJPlatformArgumentProvider.coroutinesJavaAgentFile`.
- `intellijPlatform.pluginConfiguration.description` appends content instead of replacing it [#1744](../../issues/1744)
- The `disabledPlugins.txt` file is not updated when disabling bundled plugins [#1745](../../issues/1745)
- Plugin sandbox created by `IntelliJPlatformTestingExtension` is not correct for `localPlugin(org.gradle.api.artifacts.ProjectDependency)` [#1743](../../issues/1743)  
- Better resolving the JVM variant of the `io.github.pdvrieze.xmlutil` dependency [#1741](../../issues/1741)
- Remove the inclusion of all IntelliJ Platform v2 bundled modules to the classpath [#1761](../../issues/1761)
- Duplicate bundled template error in tests [#1755](../../issues/1755)
- Fixed IDE problem when submodule jars appear as External Libraries
- Fixed `java.util.ConcurrentModificationException` on Gradle sync caused by the `pluginVerification` configuration [#1714](../../issues/1714)
- Fixed Configuration Cache issues related to the `intellijPlatform.buildSearchableOptions` flag

## [2.0.1] - 2024-08-08

### Changed

- Don't register the `testIdeUi` task by default
- Add `DisableCachingByDefault` to `PrepareSandboxTask` [#1721](../../issues/1721)
- Make the `prepareSandbox` task always run whenever any of the sandbox directories is missing [#1730](../../issues/1730)

### Fixed

- Fixed "No IDE resolved for verification with the IntelliJ Plugin Verifier" when `untilBuild` is an empty string [#1717](../../issues/1717)
- Dependency resolution fails if a repository with `exclusiveContent` rules is added twice – use `content` inclusive rule instead [#1728](../../issues/1728)
- Apply `composedJar` library elements attribute to `testRuntimeClasspath` configuration
- When adding new IDEs for Plugin Verifier, do not mutate existing `intellijPluginVerifierIdes_X` configurations if present
- Respect the `sandboxDirectory` property when configuring custom tasks with `intellijPlatformTesting` extension [#1723](../../issues/1723)

## [2.0.0] - 2024-07-30

**IntelliJ Platform Gradle Plugin 2.0 is out!**

_Read the [full blog post](https://blog.jetbrains.com/platform/2024/07/intellij-platform-gradle-plugin-2-0/)._

Version 2.0 of the IntelliJ Platform Gradle Plugin is now available! Previously called the Gradle IntelliJ Plugin, this updated plugin for the Gradle build system simplifies the configuration of environments for building, testing, verifying, and publishing plugins for IntelliJ-based IDEs. Redesigned and rewritten, it addresses community-reported issues and feature requests from previous versions.

To start using version 2.0 of the IntelliJ Platform Gradle Plugin, visit the documentation pages at [IntelliJ Platform SDK | Tooling | IntelliJ Platform Gradle Plugin 2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html), where you will find a proper introduction to all of the new features and changes, a [migration guide](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-migration.html), and examples of various configurations you may be interested in.

For projects created with the IntelliJ Platform Plugin Template, we advise taking a look at the 2.0.0 pull request applied on top of the obsolete Gradle IntelliJ Plugin 1.x configuration: https://github.com/JetBrains/intellij-platform-plugin-template/pull/458/files

If you have any issues or requests, please submit them to our [GitHub Issues](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues) page or the JetBrains Platform Slack.

To submit questions or suggestions related to the [documentation](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html), please use the feedback form at the bottom of the article.

## [2.0.0-rc2] - 2024-07-26

### Added

- Groovy support
- Make `plugin(id, version, group)` dependency helper accept `group` in the format of: empty (use Marketplace groupId), `@channel`, or user-defined groupId
- Introduce `PrepareJarSearchableOptionsTask` for performance purposes
- Possibility for specifying `configurationName` when using `testFramework`, `platformDependency`, and `testPlatformDependency` dependency helpers

### Fixed

- Sandbox producer of a custom task shouldn't inherit `sandboxDirectory` from the base sandbox producer.
- Use lenient configuration when resolving JetBrains Runtime (JBR) dependencies
- Add `exclusiveContent` to the `jetbrainsRuntime()` repository definition to resolve only JBR artifacts
- Avoid recalculating the `IvyModule` for bundled plugin/module if already written to Ivy XML file

## [2.0.0-rc1] - 2024-07-19

The `2.0.0` release is completely rewritten. Please see [documentation page](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) for more details.

### Added

- Introduce `TestFrameworkType.Starter` for adding dependencies on the Starter UI testing framework.
- Added `IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.eap` property

### Changed

- JUnit4 is no longer provided via IntelliJ Platform — it is necessary to add it to the project with `testImplementation`.
- Use the actual JVM `targetCompatibility` when defining composed jar and distribution archive variants.
- `testIdeUi` is now dedicated to work with `TestFrameworkType.Starter`.
- Extend `testImplementation` configuration with dependencies from `intellijPlatformTestDependencies`.
- Move `composed-jar` and `distribution` artifacts definition to the `org.jetbrains.intellij.platform.module` plugin.
- Set the default value of `IntelliJPlatformPluginsExtension.robotServerPlugin` to `Constraints.LATEST_VERSION`

### Fixed

- Fixed the broken resolution of the dependency on a local IntelliJ Platform.
- Fixed renaming files with the same name when preparing the sandbox.
- Fixed the resolving of the IDEs list for `verifyPlugin`.
- ProductReleasesValueSource: pick the IDE with the highest `build` number instead of `version`.
- Exclude JUnit4 (`junit4.jar`) from the IntelliJ Platform classpath
- Use `Path.invariantSeparatorsPathString` in `ModuleDescriptorsValueSource` to collect modules for exclusion on Windows
- Use the custom task configuration when calling `IntelliJPlatformPluginsExtension.robotServerPlugin(version)`

### Removed

- Removed `intellijPlatform.verifyPlugin.downloadDirectory` and `intellijPlatform.verifyPlugin.homeDirectory` as IDEs cache for Plugin Verifier is now managed with Gradle.

## [2.0.0-beta9] - 2024-07-12

### Added

- Added `useInstaller: Boolean` property to `create(type, version)` (and product-specific) dependency helpers to distinguish the type of artifacts; `true` by default
- Added `useInstaller: Boolean` property to configuration when creating custom tasks with `intelliJPlatformTesting`; `true` by default
- Introduce `create(notation: String, useInstaller: Boolean)` dependency helper for adding a dependency on the IntelliJ Platform using notation string, like `IU-2024.2`
- Introduce `jetbrainsRuntimeLocal(localPath: String)` dependency helper for adding a dependency on the local JetBrains Runtime instance
- Introduce `GradleProperties` helper class for handling `org.jetbrains.intellij.platform.<propertyName>` Gradle properties, accepting multiple input types
- `intellijPlatform` component for accessing the composed Jar with `components["intellijPlatform"]`

### Changed

- Rename `jetBrainsCdn()` repository helper to `jetbrainsIdeInstallers()`
- Rename `binaryReleasesAndroidStudio()` repository helper to `androidStudioInstallers()`
- Rewrite the latest/closest version resolution mechanism for performance reasons
- Rewrite the latest Gradle plugin check for performance reasons
- Enhance `PrintBundledPluginsTask` output
- IntelliJPlatformTestingExtension: make the produced object `Buildable` so it can be used for `dependsOn()` purposes
- Rewrite the local Ivy dependencies management
- Review the bundled plugins resolution
- `bundledPlugin()`: provide a helpful message when specifying a well-known plugin path (valid in 1.x) instead of real plugin ID (`java` vs `com.intellij.java`) 
- Renamed `org.jetbrains.intellij.platform.buildFeature.<propertyName>` Gradle properties to `org.jetbrains.intellij.platform.<propertyName>`
- `localPlugin(project(":submodule"))` refers now to the distribution Zip archive

### Fixed

- `testFramework()` dependency helper must use `DependencyVersion.Closest` instead fixed `DependencyVersion.IntelliJPlatform`
- Fixed `Task ... uses output .intellijPlatform/coroutines-javaagent.jar of task ... without declaring dependency`
- Fixed the wrong Android Studio installer architecture on `x86`
- Fixed `InvalidPathException: Illegal char <:>` exception on Windows when resolving IntelliJ Platform system properties
- Fixed missing custom plugins in the sandbox when running a custom task

### Removed

- Resolving IntelliJ Platform artifacts from JetBrains CDN using common coordinates
- Remove `BundledPluginsListTransformer` and in-advance bundled plugins resolving with JSON serialization
- Remove `BuildFeature.USE_CLOSEST_VERSION_RESOLVING`
- Remove `BuildFeature` mechanism in favor of `GradleProperties`

## [2.0.0-beta8] - 2024-07-01

### Added

- `intellijPlatformTesting` top-level extension for registering custom tasks
- Resolving IntelliJ Platform artifacts from JetBrains CDN using common coordinates
- `jetBrainsCdn()` repository helper
- `DependencyVersion` for controlling how particular dependencies are resolved (latest/closest/match IntelliJ Platform/exact)
- Added `-Didea.l10n.keys=only` to the `buildSearchableOptions` task

### Changed

- Custom tasks registering refactoring
- `testIdeUi` no longer runs IDE with Robot Server Plugin applied
- `defaultRepositories()` repository helper executes now `jetBrainsCdn()` instead of `binaryReleases()`

### Fixed

- Fixed `Cannot snapshot ../system/jcef_cache/SingletonSocket: not a regular file` issue when preparing sandbox
- Optimized resolving the latest/closest dependency version from available Maven repositories

### Removed

- `CustomRunIdeTask`, `CustomTestIdeTask`, `CustomTestIdePerformanceTask`, `CustomTestIdeUiTask` custom task classes
- `CustomIntelliJPlatformVersionAware`, `SandboxProducerAware` task aware class
- `binaryReleases()` repository helper
- `org.jetbrains.intellij.platform.buildFeature.useBinaryReleases` flag

## [2.0.0-beta7] - 2024-06-14

### Added

- `VerifyPluginProjectConfigurationTask`: limit specific checks when `.module` plugin is only applied
- `TestIdeUiTask` (`testIdeUi` task) + `CustomTestIdeUiTask` implementation
- Dependencies extension: `platformDependency(groupId, artifactIt)` and `testPlatformDependency(groupId, artifactIt)` for adding dependencies on artifacts published to the IntelliJ Maven Repository
- `TestFrameworkType.Metrics` for adding metrics and benchmarking tools for Test Framework
- More info-level logging for `ExtractorTransformer`

### Changed

- Publish instrumented and composed artifact with variants instead of replacing the default artifact
- Check the latest plugin version against Gradle Plugin Portal
- Avoid calling `checkPluginVersion` and `createCoroutinesJavaAgentFile` methods when in a `.module`
- Rename `TestFrameworkType.Platform.JUnit4` to `TestFrameworkType.Platform`
- Rename `TestFrameworkType.Platform.JUnit5` to `TestFrameworkType.JUnit5`
- Rename `TestFrameworkType.Platform.Bundled` to `TestFrameworkType.Bundled`
- Prevent from updating the `IvyModule.Info.publication` with the current time as it breaks the configuration cache

### Fixed

- Customizing the `sandboxDirectory` and `sandboxSuffix` when configuring `SandboxAware` tasks
- Fixed content exclusion when extracting DMG archives of IntelliJ Platform on macOS
- Could not find a field for name `metadata/modelVersion` (Attribute) in `MavenMetadata`
- `PluginArtifactoryShim`: use only host when setting up proxy for custom plugin repositories
- Fixed searchable options resolving on `2024.2+`

## [2.0.0-beta6] - 2024-06-06

### Added

- Custom plugin repositories with authorization headers support

### Changed

- Resolve Plugin Verifier IDEs using regular IntelliJ Platform dependency resolution

### Fixed

- Add `idea.classpath.index.enabled=false` to tests system properties to avoid creating `classpath.index` file
- Replace base archive file of the `Jar` task with `ComposedJarTask` archive file in all configuration artifact sets
- Redundant whitespace when parsing plugin dependency IDs
- Plugin Verifier: introduce partial configuration for resolving IntelliJ Platform dependencies with same coordinates but different versions

## [2.0.0-beta5] - 2024-05-30

### Added

- Introduce `KotlinMetadataAware` interface to provide metadata about the Kotlin setup

### Fixed

- Regression: Cannot fingerprint input property `productInfo`
- Regression: `GenerateManifestTask` property `kotlinStdlibBundled` doesn't have a configured value
- Regression: `PrepareSandboxTask` doesn't create `system` and `log` sandbox directories
- Revise creating custom tasks and IntelliJ Platform main dependency inheritance

## [2.0.0-beta4] - 2024-05-27

### Added

- Support for Android Studio DMG archives
- Introduce `VerifyPluginProjectConfigurationTask.hasModulePlugin` to exclude modules using `org.jetbrains.intellij.platform.module` subplugin from `plugin.xml` checks.
- Better error handling in dependency helpers when missing values
- Introduce `GenerateManifestTask` for generating `MANIFEST.MF` file
- Introduce `ComposedJarTask` to compose and pick the final jar archive
- Introduce `intellijPlatform.pluginModule(Dependency)` dependency helper to compose a single jar combined of multiple modules

### Fixed

- Avoid leaking internal properties from `intellijPlatform` extensions
- Fixed custom tasks suffixing
- Fixed: Task `:test` uses this output of task `:prepareSandbox` without declaring an explicit or implicit dependency [#1609](../../issues/1609)
- ExtractorTransformer: Exclude only `Applications` symlink
- SandboxAware: inherit sandbox directory from producer
- Add IntelliJ Platform path-based hash to Ivy files to better deal with cache (temporary workaround)

## [2.0.0-beta3] - 2024-05-18

### Added

- `jetbrainsRuntime()` dependency helper for resolving a suitable JBR version for IntelliJ Platform fetched from IntelliJ Maven Repository
- `jetbrainsRuntimeExplicit(explicitVersion)` dependency helper for specifying an explicit JBR version if necessary
- `PrepareSandboxTask`: introduce `sandboxDirectoriesExistence` property to ensure all sandbox directories exist
- `localPlugin()` dependency helper for adding local plugins as project dependencies and extending customizable tasks
- Emit warning when using the `bundledLibrary` dependency helper.
- Use IntelliJ Platform distribution from [download.jetbrains.com](http://download.jetbrains.com/) by default. To switch back to IntelliJ Maven Repository artifacts, use `org.jetbrains.intellij.platform.buildFeature.useBinaryReleases=false`
- Introduced `Custom*` tasks. if you want to extend the `runIde` or `testSomething` tasks, use the `Custom*Task` classes. See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-custom-tasks.html
- Better handling of missing dependencies/misconfiguration
- Bring back the `SetupDependenciesTask` to avoid failing build when migrating from `1.x`
- Better `ClosestVersionResolver` error messages
- When running IDE in Split Mode, it’s possible to specify `splitModeTarget` (`BACKEND`, `FRONTEND`, `BACKEND_AND_FRONTEND`)

### Changed

- Update `PlatformJavaVersions` and `PlatformKotlinVersions`
- Introduce a separated Sandbox for the Frontend part when running IDE in Split Mode
- Rename `SandboxAware.sandboxContainerDirectory` to `SandboxAware.sandboxDirectory` to avoid confusion with `intellijPlatform.sandboxContainer`
- Use custom task name as a suffix for dynamically created configuration and tasks instead of `UUID.randomUUID()`

### Fixed

- Fixed transitive dependencies of bundled plugin dependencies when IntelliJ Platform doesn't contain `ProductInfo.layout` model yet.
- Produce customized (suffixed) configuration only for `CustomIntelliJPlatformVersionAware` tasks
- Fixed including transitive modules/bundled plugins dependencies of declared plugin dependencies
- Fixed JetBrains Runtime (JBR) resolving
- Move `TestFrameworkType` from `org.jetbrains.intellij.platform.gradle.extensions` to `org.jetbrains.intellij.platform.gradle`

### Removed

- Dropped `testIde` task as `test` is now properly configured

## [2.0.0-beta2] - 2024-05-14

### Added

- Use IntelliJ Platform distribution from [download.jetbrains.com](http://download.jetbrains.com/) by default. To switch back to IntelliJ Maven Repository artifacts, use `org.jetbrains.intellij.platform.buildFeature.useBinaryReleases=false`
- Introduced `Custom*` tasks. if you want to extend the `runIde` or `testSomething` tasks, use the `Custom*Task` classes. See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-custom-tasks.html
- Better handling of missing dependencies/misconfiguration
- Bring back the `SetupDependenciesTask` to avoid failing build when migrating from `1.x`
- Better `ClosestVersionResolver` error messages
- When running IDE in Split Mode, it’s possible to specify `splitModeTarget` (`BACKEND`, `FRONTEND`, `BACKEND_AND_FRONTEND`)

### Fixed

- Fixed including transitive modules/bundled plugins dependencies of declared plugin dependencies
- Fixed JetBrains Runtime (JBR) resolving
- Move `TestFrameworkType` from `org.jetbrains.intellij.platform.gradle.extensions` to `org.jetbrains.intellij.platform.gradle`

### Removed

- Dropped `testIde` task as `test` is now properly configured

## [2.0.0-beta1] - 2024-04-11

The `2.0.0` release is completely rewritten. Please see [documentation page](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) for more details.

## [1.17.4] - 2024-06-14

### Added

- Message about required migration to IntelliJ Platform Gradle Plugin 2.0 when targeting IntelliJ Platform 2024.2+ (242+).

## [1.17.3] - 2024-03-29

### Fixed

- Fix for: `coroutinesJavaAgentPath` specifies file `.../build/tmp/initializeIntelliJPlugin/coroutines-javaagent.jar` which doesn't exist
- Fixed resolving Android Studio releases URL for Windows [#1551](../../issues/1551)
- Fixed repository type classification for Rider RC builds [#1579](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1579)

## [1.17.2] - 2024-02-20

### Added

- Set the `idea.module.downloadSources` flag with `intellij.downloadSources` value

## [1.17.1] - 2024-02-05

### Fixed

- Fix for running `classpathIndexCleanup` task in the proper order
- Resolve JetBrains Runtime (JBR) 21 to JCEF variant

## [1.17.0] - 2024-01-18

### Added

- Publish the plugin update and mark it as hidden to prevent public release after approval, using the `publishPlugin.hidden` property.
- PatchPluginXmlTask: Wrap the content passed to `<change-notes>` and `<description>` elements with `<![CDATA[ ... ]]>` [#1498](../../issues/1498)

### Changed

- New project name: IntelliJ Platform Gradle Plugin
- New project ID: `org.jetbrains.intellij.platform`
- New Maven coordinates: `org.jetbrains.intellij.platform:intellij-platform-gradle-plugin`
- Move classes under the new package: `org.jetbrains.intellij.platform.gradle`
- Update minimal supported Gradle version to `8.2`

### Fixed

- Do not discover `idea.platform.prefix` by scanning shell scripts for `223+` [#1525](../../issues/1525)
- MemoizedProvider incompatible with Gradle 8.6 RC1 [#1517](../../issues/1517)
- Tasks `:classpathIndexCleanup` and `:compileTestKotlin` don't have a declared dependency causing build to fail [#1515](../../issues/1515)
- ListProductsReleases empty for `2023.3` [#1505](../../issues/1505)

## [1.16.1] - 2023-12-01

### Added

- Make RustRover (`RR` type) available for resolving as SDK.

### Fixed

- Attach IntelliJ SDK sources before LSP API sources [#1490](../../issues/1490)
- Fixed `RunPluginVerifierTask.FailureLevel.NOT_DYNAMIC` presence check [#1485](../../issues/1485)

## [1.16.0] - 2023-10-06

### Added

- Configure all tasks that extend task classes instead of just those created by the plugin
- Make `JbrResolver` prefer Gradle `javaToolchains` by `JetBrains` vendor, if already available.
- Support for Kotlin Coroutines debugging
- Detect and warn if project adds an explicit dependency on Kotlin Coroutines library
- `RunPluginVerifierTask`: new `runPluginVerifier.verificationReportsFormats` property to control verifier output formats
- `RunPluginVerifierTask`: new `runPluginVerifier.ignoredProblems` property to include a file with list of problems to be ignored in a report
- `RunPluginVerifierTask`: new `runPluginVerifier.freeArgs` property to let pass to the IntelliJ Plugin Verifier custom arguments

### Fixed

- `NoClassDefFoundError: org/gradle/api/publish/ivy/internal/publication/DefaultIvyPublicationIdentity` in Gradle 8.4 [#1469](../../issues/1469)
- Misleading message about Kotlin API version [#1463](../../issues/1463)

### Changed

- Disabled caching for `BuildPluginTask`
- Deprecate `SetupDependenciesTask`

### Removed

- Removed `intellij`, `intellijPlugin`, `intellijPlugins`, `intellijExtra` helper methods from `DependenciesUtils`

## [1.15.0] - 2023-07-07

### Added

- Attach LSP API sources to the IDEA dependency, if available
- Added `ListProductsReleasesTask.androidStudioProductReleasesUpdateFiles` property
- Added `DownloadAndroidStudioProductReleasesXmlTask` task
- Introduced `DownloadAndroidStudioProductReleasesXmlTask.releasesUrl` and `DownloadIdeaProductReleasesXmlTask.releasesUrl`
  properties [#1418](../../issues/1418)

### Changed

- Renamed `ListProductsReleasesTask.productsReleasesUpdateFiles` property to `ListProductsReleasesTask.ideaProductReleasesUpdateFiles`

### Removed

- Removed `ListProductsReleasesTask.updatePaths` property

## [1.14.2] - 2023-06-26

### Added

- Create a date-based lock file to limit daily update checks for the Gradle IntelliJ Plugin.

### Fixed

- Handle the `Could not HEAD 'https://www.jetbrains.com/updates/updates.xml'` gracefully when running `downloadIdeaProductReleasesXml` with no Internet
  connection
- Improved checking if `Provider` holds non-empty value
- Fixed calculationg of JVM arguments for running tests [#1360](../../issues/1360)
- Introduce CommandLineArgumentProviders for better management of JVM arguments and avoiding passing absolute paths to support Gradle Build
  Cache [#1376](../../issues/1376)
- Replace deprecated `JavaPluginConvention` usages with `JavaPluginExtension` for Gradle 8.2 and 9.x compatibility [#1413](../../issues/1413)
- Fix for `Cannot load this JVM TI agent twice, check your java command line for duplicate jdwp options.`

### Removed

- Removed redundant `SetupInstrumentCodeTask` task

## [1.14.1] - 2023-06-07

### Fixed

- `Illegal char <:> at index 25: -Djna.boot.library.path=...` exception on Windows when calculating the IDE home path

## [1.14.0] - 2023-06-02

### Added

- VerifyPluginConfigurationTask: Kotlin version check — report OOM for Kotlin `1.8.20+`, see: https://jb.gg/intellij-platform-kotlin-oom

### Fixed

- Resolving Android Studio JNA libraries on macOS [#1353](../../issues/1353)
- Fixed "Must not use `executable` property on `Test` together with `javaLauncher` property" for Gradle `7.x` [#1358](../../issues/1358)
- Task `:listProductsReleases` creates empty file due to `MalformedByteSequenceException` [#1389](../../issues/1389)
- Make `RunIdeBase.pluginsDir` a `@Classpath` input, fixes cacheability of `buildSearchableOptions` [#1370](../../issues/1370)
- Fixed `JarSearchableOptionsTask` cacheability [#1375](../../issues/1375)

### Changed

- Set minimum supported Gradle version from `7.3` to `7.6`

## [1.13.3] - 2023-03-28

### Added

- Run tests using JBR [#473](../../issues/473)
- Introduce `verifyPluginSignature` task for verification signed plugin archive produced by `signPlugin` task
- Provide KDoc documentation for all tasks and extensions [#1345](../../issues/1345)

### Fixed

- Add instrumented classes (sources + tests) to the tests classpath before tests execution [#1332](../../issues/1332)
- Fixed `NSDictionary` helper methods to return `null` instead of `"null"` — causing "Resource not found:
  /idea/nullApplicationInfo.xml" [#1348](../../issues/1348)

## [1.13.2] - 2023-03-10

### Fixed

- Add instrumented classes (sources + tests) to the tests classpath [#1332](../../issues/1332)

## [1.13.1] - 2023-03-02

### Added

- Provide `idea.log.path` system property for `RunIde`-based tasks and tests

### Fixed

- Unsupported JVM architecture was selected for running Gradle tasks: `x86_64` [#1317](../../issues/1317)
- Instrumentation ignores `intellij.instrumentCode = false` [#1310](../../issues/1310)
- `NoClassDefFoundError: org/jetbrains/kotlin/konan/file/FileKt` when running `signPlugin` task on Gradle lower than 8.0 [#1319](../../issues/1319)
- `taskdef class com.intellij.ant.InstrumentIdeaExtensions cannot be found` when running instrumentation on Android Studio [#1288](../../issues/1288)
- JVM arguments mangled since `1.10` resulting in `ClassNotFoundException` for `PathClassLoader` [#1311](../../issues/1311)
- Add missing compiled classes to the instrumentation task classpath
- Mark `RunPluginVerifierTask.FailureLevel.ALL` and `RunPluginVerifierTask.FailureLevel.NONE` with `@JvmField` annotation [#1323](../../issues/1323)

## [1.13.0] - 2023-02-10

### Added

- Support for Gradle `8.0`
- Introduced the `initializeIntelliJPlugin` task for executing plugin initialization actions, like `checkPluginVersion`
- `instrumentJar` task to produce independent jar file with instrumented classes
- `instrumentedJar` configuration for multi-modules projects
- Publish plugin marker to the Maven Snapshot Repository

### Fixed

- Don't enforce the Kotlin version of the project by using `compileOnly` instead of `api` when declaring the `org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0`
  dependency [#1276](../../issues/1276)
- Instrumentation: fixed configuration cache support, dropped the `postInstrumentCode` and `postInstrumentTestCode` tasks [#1283](../../issues/1283)

### Breaking Changes

- It is required to use the `instrumentedJar` configuration when referring submodules in multi-modules project,
  like `dependencies { implementation(project(":submodule", "instrumentedJar")) }`
- Rename `IntelliJInstrumentCodeTask` to `InstrumentCodeTask`

## [1.12.0] - 2023-01-13

### Added

- Extract ZIP Signer CLI tool downloading as `downloadZipSigner` task
- Allow for passing `signPlugin.privateKey` and `signPlugin.certificateChain` as base64-encoded value

### Changed

- Download IDEs used by the Plugin Verifier in the task execution phase.

### Fixed

- Added missing incremental task annotation properties and cacheability annotations [#1258](../../issues/1258)
- Make `listBundledPlugins` not printing output as `printBundledPlugins` does that
- Fixed `taskdef class com.intellij.ant.InstrumentIdeaExtensions cannot be found` [#1259](../../issues/1259)
- Don't warn about unexpected instrumentation task name [#1214](../../issues/1214)

## [1.11.0] - 2022-12-17

### Added

- `printProductsReleases` task to print the result of the `listProductsReleases` task
- `printBundledPlugins` task to print the result of the `listBundledPlugins` task
- `runIde.jbrArch` and `runPluginVerifier.jbrArch` properties for the explicit JBR architecture specification

### Changed

- `custom(String)` helper of `intellij.pluginRepositories` configuration requires now passing a direct URL to the `updatePlugins.xml`
  file [#1252](../../issues/1252)
- `listProductsReleases` task doesn't print output anymore
- `listBundledPlugins` task doesn't print output anymore
- Set minimum supported Gradle version to `7.3`

### Fixed

- Replace `Contents/Contents` part within JVM arguments to a single `Contents` – happens with macOS distribution
- `--offline` prevents from using JBR even if it is already downloaded [#1251](../../issues/1251)

## [1.10.2] - 2022-12-16

### Changed

- Revert back the minimum supported Gradle version to `6.8`

## [1.10.1] - 2022-12-08

### Changed

- Set minimum supported Gradle version from `6.8` to `7.1`

### Fixed

- Fixed "Error: Could not find or load main class" when using older SDK versions
- Fix launch information could not be found for macOS. [#1230](../../issues/1230)
- Fixed "Cannot change dependencies of dependency configuration ... after it has been included in dependency resolution" [#1209](../../issues/1209)

## [1.10.0] - 2022-11-17

### Added

- Set `IDEA_PLUGIN_SANDBOX_MODE` to `true` for `runIde`-based tasks
- The `listBundledPlugins` task for listing IDs of plugins bundled within the currently targeted IDE
- Make sure `1.10.0` is higher than `1.10.0-SNAPSHOT` in version check [#1155](../../issues/1155)

### Fixed

- Invalidate instrumented classes bound to forms if GUI changed [IDEA-298989](https://youtrack.jetbrains.com/issue/IDEA-298989/Duplicate-method-name-getFont)
- Revert pushing project resource directories to the end of classpath in the test task context. ([#1101](../../issues/1101))
- Avoid unnecessary task configuration during Gradle configuration phase [#1110](../../issues/1110) by @3flex
- Replace internal Gradle ConventionTask with DefaultTask [#1115](../../issues/1115) by @aSemy
- Plugin Verifier cache directory now follows XDG cache standards [#1119](../../issues/1119) by @aSemy
- Migrate most of the Gradle API in `IntelliJPlugin.kt` to use the Gradle Kotlin DSL extensions [#1117](../../issues/1117) by @aSemy
- Support `runIde.jbrVersion` in `17.0.4.1-b653.1` format [#1172](../../issues/1172)
- Plugin dependencies not resolved in multi-module project [#1196](../../issues/1196)
- Finalize instrumentation with `classpathIndexCleanup` run to remove `classpath.index` file which breaks incremental build
- Misleading message about Kotlin language version [#1156](../../issues/1156)
- Fix launch information could not be found for macOS.[#1230](../../issues/1230)

### Changed

- Set minimum supported Gradle version from `6.7.1` to `6.8`
- Use information from `product-info.json` for running `223+`

## [1.9.0] - 2022-09-02

### Added

- Configure classpath for run-based tasks using `package-info.json` provided with IntelliJ SDK 2022.3+
- The [`verifyPluginConfiguration`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-verifypluginconfiguration) task for
  validating the plugin project configuration.
- Make Android Studio (`AI` type) available for resolving as SDK.

### Changed

- Change `IntelliJPluginConstants.ANDROID_STUDIO_PRODUCTS_RELEASES_URL` to `https://jb.gg/android-studio-releases-list.xml`

## [1.8.1] - 2022-08-24

### Added

- Configure classpath for run-based tasks using `Info.plist` provided with IntelliJ SDK 2022.3+

### Changed

- OpenedPackages: add `java.desktop/java.awt.font` for all OSes

## [1.8.0] - 2022-08-04

### Added

- Add `sourceSets` output directories to the classpath of the `test` task.
- Synchronize `OpenedPackages` list with
  the [latest version](https://raw.githubusercontent.com/JetBrains/intellij-community/master/plugins/devkit/devkit-core/src/run/OpenedPackages.txt) available.
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

## [1.7.0] - 2022-07-08

### Added

- Automatically detect bundled sources in plugin dependency [#786](../../issues/786)
- Automatically detect plugin dependency sources provided in the IDE distribution [#207](../../issues/207)
- Throw an error when `intellij.version` is missing [#1010](../../issues/1004)
- Set `ResolutionStrategy.SortOrder.DEPENDENCY_FIRST` for `compileClasspath` and `testCompileClasspath` configurations [#656](../../issues/656)
- Added `useDependencyFirstResolutionStrategy` feature flag.
  See [Feature Flags](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#build-features).
- Ensure `classpath.index` is not bundled in the JAR file
- Warn about no settings provided by the plugin when running `buildSearchableOptions` and
  suggest [disabling the task](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-options). [#1024](../../issues/1024)
- Warn about paid plugin running `buildSearchableOptions` and
  suggest [disabling the task](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-options). [#1025](../../issues/1025)
- IDE dependencies are added to the `compileOnly` classpath for test fixtures if the `java-test-fixtures` plugin is applied [#1028](../../issues/1028)
- `classpathIndexCleanup` task is added to remove `classpath.index` files created by `PathClassLoader` [#1039](../../issues/1039)
- Improve Plugin Verifier error messages [#1040](../../issues/1040)
- Added `FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES` to the Plugin Verifier task
- Support for JetBrains Runtime 2022.2 directories layout [#1016](../../issues/1016)

### Changed

- Set minimum supported Gradle version from `6.7` to `6.7.1`
- Resolve dependencies using repositories in the following order: project custom repositories (if any), plugin custom repositories, common repositories (like
  Maven Central)
- Add executable flag for `Rider.Backend` native launchers
  in `IdeaDependencyManager#resetExecutablePermissions` [RIDER-59978](https://youtrack.jetbrains.com/issue/RIDER-59978)
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

## [1.6.0] - 2022-05-23

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

## [1.5.3] - 2022-04-15

- Updated dependencies marked as vulnerable
- Fixed code instrumentation disabling via `tasks.instrumentCode.enabled`
- `instrumentCode` task – limit the scope of the task to `sourceSets.main.java` [#459](../../issues/459)
- Introduce Gradle IntelliJ Plugin version check against the latest available

## [1.5.2] - 2022-03-31

- Add `util_rt.jar` to the classpath of run-like tasks for `2022.1+` compatibility

## [1.5.1] - 2022-03-28

- Make IDEA products releases cached hourly [#848](../../issues/848)
- Fixed `ListProductReleasesTask` to return only significant versions for Android Studio [#928](../../issues/928)

## [1.5.0] - 2022-03-28

- Include Android Studio builds in the `ListProductsReleasesTask` results
- Fix compiler resolution for EAP versions [#906](../../issues/906)
- Initial Toolbox Enterprise integration [#913](../../issues/913)
- Make IDEA products releases cached daily [#848](../../issues/848)
- Fixed `ListProductsReleasesTask` to allow for empty `untilBuild` [#909](../../issues/909)
- Resolved closest lower java-compiler-ant-tasks version if provided isn't available [#910](../../issues/910)
- Fixed XML parsing with JAXB - drop intermediate JDOM Document

## [1.4.0] - 2022-02-11

- Fixed JBR resolving for MacOSX M1
- Fixed compiler resolution for long build numbers [#883](../../issues/883)
- Build number fallback when `product-info.json` is missing [#880](../../issues/880)
- Consider `sinceBuild` and `untilBuild` properties of `ListProductsReleasesTask` in task caching [#891](../../issues/891)
- Introduce `jbrVariant` property next to the `jbrVersion` property in `runIde`, `runPluginVerifier`, `buildSearchableOptions`, and `runIdeForUiTest`
  tasks [#852](../../issues/852)
- Change log level of `JbrResolver.resolveRuntime` logs from `warn` to `debug` [#849](../../issues/849)
- Update runtime classpath for `221+`
- Fixed resolving Java Runtime for MacOSX [#895](../../issues/895)
- ProductInfo: parse custom properties in `product-info.json` [#897](../../issues/897)
- Make `IntelliJInstrumentCodeTask` incremental

## [1.3.1] - 2021-11-17

- Fixed execution bit filter when extracting Rider [RIDER-72922](https://youtrack.jetbrains.com/issue/RIDER-72922)
- Revert `org.jetbrains.intellij:blockmap` dependency to the latest `1.0.5` version
- Avoid querying `intellij.version` when `intellij.localPath` is set
- Fixed `BuildSearchableOptionsTask` for `2022.1+` version of IDE [RIDER-73264](https://youtrack.jetbrains.com/issue/RIDER-73264)
- `ListProductsReleasesTask`: rely on the `patchPluginXml.sinceBuild`/`patchPluginXml.untilBuild` properties instead of `intellij.version`
- `ListProductsReleasesTask`: allow using IDE version along with build numbers

## [1.3.0] - 2021-11-15

- IntelliJ Plugin Verifier allows for running against Android Studio (i.e. `AI-2021.2.1.4`)
- Make `intellij.version` property mandatory
- Move `intellij.ideaDependency` to the `SetupDependenciesTask.idea`
- Postpone the initial dependencies downloading to the `setupDependencies` task which is run in the `afterSync` phase or by individual tasks
- Provide build information within the `META-INF/MANIFEST.MF` file
- Resolve EAP version of the Java compiler for `LATEST-EAP-SNAPSHOT`
- Allow for using `dcevm`, `fd`, and `nomod` variants of JBR [#818](../../issues/818)
- `ListProductsReleasesTask.updatesPath` changed to `ListProductsReleasesTask.updatePaths`
- `ListProductsReleasesTask.includeEAP` changed to `ListProductsReleasesTask.releaseChannels`

## [1.2.1] - 2021-10-26

- Respect `ideaDependencyCachePath` property [#794](../../issues/794)
- Fix for providing dependencies after project evaluation [#801](../../issues/801)
- Resolve EAP version of the Java compiler for local EAP IDE instances [#811](../../issues/811)
- Allow for passing an empty array for `runPluginVerifier.ideVersions` property [#809](../../issues/809)

## [1.2.0] - 2021-09-30

- Fixed running tests on 2021.3 platform version
- Avoid downloading IDE dependency in configuration phase
- Deprecate `IntelliJPluginExtension.getIdeaDependency(project: Project)`
- Increase the default `runPluginVerifier.failureLevel` to `COMPATIBILITY_PROBLEMS`
- Introduce `listProductsReleases` task for listing the IDE releases matching given criteria
- Fixed resolving compiler classpath for the `instrumentCode` task when using `LATEST-EAP-SNAPSHOT` [#752](../../issues/752)
- Fixed resolving `idea.platform.prefix` [#772](../../issues/772)
- Fix for custom `buildDir` not used in some `instrumentCode` and `buildSearchableOptions` tasks [#793](../../issues/793)

## [1.1.6] - 2021-09-05

- Fixed returned list of paths to IDEs downloaded for Plugin Verifier [#779](../../issues/779)

## [1.1.5] - 2021-09-03

- Use target Kotlin API Version 1.3 [#750](../../issues/750)
- Migrate `SignPluginTask` to use the Marketplace ZIP Signer CLI
- Fixed resolving of built-in JetBrains Runtime (JBR) [#756](../../issues/756)

## [1.1.4] - 2021-07-21

- Configuration cache enhancements
- Fix `prepareTestingSandbox` not running when test task is executed [#745](../../issues/745) by @abrooksv
- Move signPlugin file name creation to lazy [#742](../../issues/742) by @brian-mcnamara
- Better platform prefix resolving

## [1.1.3] - 2021-07-14

- Fixed dependency on `JavaScript` plugin [#674](../../issues/674)
- Fixed `releaseType` resolving for Rider versions in `-EAP#-SNAPSHOT` format.
- `runPluginVerifier`: verify required Java 11 environment for Plugin Verifier `1.260+`
- `pluginVerifier` – remove support for old versions `< 1.255` hosted on Bintray
- Fixed tests configuration – 'Config Directory' does not exist exception

## [1.1.2] - 2021-07-01

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

## [1.0.0] - 2021-05-27

- Breaking changes guide: https://lp.jetbrains.com/gradle-intellij-plugin
- Plugin Signing integration
- Lazy Configuration support
- Configuration Cache support
- Task Configuration Avoidance support
- better CI (GitHub Actions, Qodana, Dependabot)
- Rewritten in Kotlin
- property names cleanup (`*Repo` to `*Repository`, `*Directory` to `*Dir` – for the sake of consistency with Gradle)
- Stepping away from Bintray and JCenter

## [0.7.3] - 2021-04-26

- migrate from bintray [#594](../../issues/594)
- exclude kotlin-reflect and kotlin-text from the runtime if kotlin is used in plugin [#585](../../issues/585)
- respect overridden `build` directory [#602](../../issues/602)
- store cache of plugins from different custom repositories in different directories [#579](../../issues/579)
- rename dependency jars with the same name [#497](../../issues/497)

## [0.7.2] - 2021-02-23

- fix classpath for IDE without `ant` inside distribution
- fix resolving the OS architecture

## [0.7.1] - 2021-02-22

- fix classpath for IDE 2020.2 [#601](../../issues/601)

## [0.7.0] - 2021-02-21

- support GoLand as an SDK
- fix javac2 dependency for project with implicit IntelliJ version [#592](../../issues/592)
- fix using query parameters in custom repository urls [#589](../../issues/589)
- support downloading JBR for aarch64 [#600](../../issues/600)
- added ant dependencies to testing classpath
- fix JBR resolving after removing JavaFX from JBR in IDEA 2021.1 [#599](../../issues/599)

## [0.6.5] - 2020-11-25

- fixed not found classes from plugin dependencies in tests [#570](../../issues/570)

## [0.6.4] - 2020-11-19

- runPluginVerifier: integrate Plugin Verifier offline mode with Gradle `offline` start parameter
- runPluginVerifier: introduce `verifierPath` property
- support for Rider for Unreal Engine as an SDK

## [0.6.3] - 2020-11-09

- fixed loading dependencies of builtin plugin [#542](../../issues/542)
- fixed loading file templates from plugins [#554](../../issues/554)
- yet another fix for class-loading in tests for IntelliJ Platform 203 and higher [#561](../../issues/561)

## [0.6.2] - 2020-11-05

- runPluginVerifier: make ideVersions property mandatory
- runPluginVerifier: better handling of the exception produced by DownloadAction [#553](../../issues/553)
- runPluginVerifier: provide URL for verifying the available IDE versions [#553](../../issues/553)
- runPluginVerifier: fix java.nio.file.FileAlreadyExistsException as ERROR in logs [#552](../../issues/552)
- add prepareTestingSandbox as an input to tests

## [0.6.1] - 2020-10-29

- runPluginVerifier: allow specifying `ideVersions` as comma-separated String
- runPluginVerifier: specifying EAP build number leads to IllegalArgumentException
- runPluginVerifier: fix for `ArrayIndexOutOfBoundsException` when destructuring `ideVersion.split`

## [0.6.0] - 2020-10-29

- Introduced runPluginVerifier task that runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IntelliJ IDE builds.

## [0.5.1] - 2020-10-27

- fix class-loading in tests for IntelliJ Platform >= 203

## [0.5.0] - 2020-10-05

- do not download dependencies during configuration phase [#123](../../issues/123)
- support multiple plugin repositories
- support enterprise plugin repositories [#15](../../issues/15)

## [0.4.26] - 2020-09-18

- fix plugin-repository-rest-client dependency

## [0.4.25] - 2020-09-17

- fix plugin-repository-rest-client dependency

## [0.4.24] - 2020-09-17

- fix plugin-repository-rest-client dependency

## [0.4.23] - 2020-09-17

- fix compatibility issue with Kotlin 1.4 serialization [#532](../../issues/532)

## [0.4.22] - 2020-09-03

- add option to disable auto-reload of dynamic plugins
- documentation improvements

## [0.4.21] - 2020-05-12

- fix adding searchable options to the distribution for Gradle > 5.1 [#487](../../issues/487)

## [0.4.20] - 2020-05-06

- fixed caching builtin plugins data
- add annotations-19.0.0 to compile classpath by default
- fix setting plugin name for Gradle 5.1-5.3 [#481](../../issues/481)

## [0.4.19] - 2020-05-02

- Use builtin JBR from alternativeIdePath IDE [#358](../../issues/358)
- Enable dependencies for builtin plugins automatically [#474](../../issues/474)
- Allow referring builtin plugins by their ids rather than directory name [IDEA-233841](https://youtrack.jetbrains.com/issue/IDEA-233841)
- Require 4.9 Gradle version, dropped deprecated stuff
- Do not add junit.jar into classpath, it may clash with junit-4.jar on certain JDKs

## [0.4.18] - 2020-04-01

- Introduced `runIdeForUiTests` task [#466](../../issues/466)
- Fix unpacking JBR with JCEF on Mac [#468](../../issues/468)
- Publish plugin security update [#472](../../issues/472)

## [0.4.17] - 2020-03-23

- Fix platform prefix for DataGrip [#458](../../issues/458)
- Enable plugin auto-reloading by default
- Upgrade plugins repository client
- Use new methods for Gradle 5.1 and higher [#464](../../issues/464)
- Support JBR with JCEF [#465](../../issues/465)

## [0.4.16] - 2020-01-27

- Fix downloading JBR if temp directory and gradle chace are on the different partitions [#457](../../issues/457)
- Build searchable options task is marked as cacheable

## [0.4.15] - 2019-12-07

- Fix uploading on Java 11 [#448](../../issues/448)
- Fix instrumentation when localPath is set [#443](../../issues/443)

## [0.4.14] - 2019-11-25

- Support for Gradle 6.0
- Deprecated `runIde.ideaDirectory`. `runIde.ideDirectory` should be used instead

## [0.4.13] - 2019-11-13

- Removed `intellij.useProductionClassLoaderInTests` option as we found another way to fix loading plugins in tests in 2019.3

## [0.4.12] - 2019-11-08

- More structured logging
- Introduced `intellij.useProductionClassLoaderInTests` option to control how plugin is going to be loaded in tests

## [0.4.11] - 2019-10-30

- Fix setting archive name for Gradle 5.1 and higher [#436](../../issues/436)
- Fix forms compilation for Rider and Python snapshot builds. Works for Rider-2019.3-SNAPSHOT and higher [#403](../../issues/403)

## [0.4.10] - 2019-08-08

- Upgrade download plugin [#418](../../issues/418)
- Simplify custom runIde task configuration [#401](../../issues/401)

## [0.4.9] - 2019-06-05

- Graceful handling of 404 errors when publishing a new plugin [#389](../../issues/389)
- Support PyCharm as an SDK
- Fail if the plugin depends on Java plugin but doesn't declare it as dependency

## [0.4.8] - 2019-04-16

- Gradle 5.4 compatibility
- Support for new JBR distributions layout
- Made buildSearchableOption task incremental

## [0.4.7] - 2019-03-25

- add one more executable file in Rider SDK

## [0.4.6] - 2019-03-25

- support Gradle 5.3 [#379](../../issues/379)
- fixed downloading JBR 8 for IDEA 2018.3 and earlier

## [0.4.5] - 2019-03-13

- support JBR 11 from the new JetBrains Runtime Repository
- support running using JBR 11 [IDEA-208692](https://youtrack.jetbrains.com/issue/IDEA-208692)

## [0.4.4] - 2019-02-28

- support the new bintray repository for JetBrains Runtime artifacts
- fixed downloading of old JBR builds [#367](../../issues/367)
- fix instrumentation for local IDE instances [#369](../../issues/369)

## [0.4.3] - 2019-02-19

- fixed downloading instrumentation dependencies for release versions
- fixed downloading renamed JetBrains Runtime artifacts

## [0.4.2]

- fixed removing `config/` and `system/` on running `runIde` task [#359](../../issues/359)

## [0.4.1]

- fixed plugin's sources attaching

## [0.4.0]

- drop Gradle 2 support
- support for CLion as a building dependency [#342](../../issues/342)
- support token-based authentication while publishing plugins [#317](../../issues/317)
- add notification about patching particular tag values and attributes in plugin.xml [#284](../../issues/284)
- fix attaching sources to bundled plugins [#337](../../issues/337)
- fix verification message in case of default value of `description`-tag

## [0.3.12]

- fixed resolving plugins from a custom channel [#320](../../issues/320)
- fixed building with Java 9

## [0.3.11]

- ~~fixed resolving plugins from a custom channel~~
- fixed uploading plugins [#321](../../issues/321)
- fixed caching strategy for IDEA dependency [#318](../../issues/318)

## [0.3.10]

- fixed dependency on local plugin files
- cache-redirector is used for downloading plugin dependencies [#301](../../issues/301)

## [0.3.7]

- fixed missing `tools.jar` on Mac [#312](../../issues/312)

## [0.3.6]

- `runIde` task uses `tools.jar` from a JBRE java [#307](../../issues/307)

## [0.3.5]

- Allow to override all system properties in RunIde task [#304](../../issues/304)
- Move to the new url to JBRE and Gradle distributions [#301](../../issues/301)
- Fixed an encoding while writing plugin.xml [#295](../../issues/295)

## [0.3.4]

- Gradle 4.8 compatibility [#283](../../issues/283)

## [0.3.3]

- fixed compiling JGoodies forms for IDEA version >= 182.* [#290](../../issues/290)

## [0.3.2]

- use tools.jar from a java of `runIde` task [IDEA-192418](https://youtrack.jetbrains.com/issue/IDEA-192418)

## [0.3.1]

- fix running for IDEA version < 2017.3 [#273](../../issues/273)

## [0.3.0]

- added plugin verification task: `verifyPlugin`
- default values of `runIde` task are propagated to all RunIdeaTask-like tasks
- enhanced plugins resolution: better error messages for unresolved dependencies and fixes [#247](../../issues/247)
- check build number to decide whether the unzipped distribution can be reused (fixes [#234](../../issues/234))
- download JetBrains Java runtime and use it while running IDE (fixes [#192](../../issues/192))
- do not include plugin's jars recursively (fixes [#231](../../issues/231))
- allow adding custom Javac2.jar to `instrumentCode` task

## [0.2.20]

- recognize new kotlin stdlib files as part of IDEA dependency

## [0.2.19]

- Setup project plugin dependency for an already evaluated project (fixes [#238](../../issues/238))

## [0.2.18]

- update default repository url
- support for running GoLand

## [0.2.17]

- fix compatibility with Gradle 4.0 new versions of Kotlin and Scala plugins (fixes [#221](../../issues/221) and [#222](../../issues/222))

## [0.2.16]

- automatically set system properties for debugging Resharper

## [0.2.15]

- restore scripts execution permissions in Rider distribution

## [0.2.14]

- support RD prefix for Rider
- avoid possible NPEs (fixes [#208](../../issues/208))

## [0.2.13]

- Gradle 4.0 compatibility fixes

## [0.2.12]

- upgrade plugin-repository-rest-client

## [0.2.11]

- upgrade plugin-repository-rest-client

## [0.2.10]

- upgrade plugin-services libraries to fix 'Invalid plugin type' exception while downloading plugins dependencies (fixes [#201](../../issues/201))
- prefer `compile` configuration for any plugins IDEA dependencies in tests (fixes [#202](../../issues/202))

## [0.2.9]

- prefer `compile` configuration for bundled plugins IDEA dependencies in tests

## [0.2.8]

- prefer `compile` configuration for IDEA dependencies in tests
- prefer `compileOnly` configuration for plugins dependencies in tests

## [0.2.7]

- avoid exception due to adding duplicated configurations

## [0.2.6]

- prefer `compileOnly` configuration for IDEA dependencies

## [0.2.5]

- set `buildDir` as a default cache for IDE dependencies in case of Rider-plugin
- fix Kotlin instrumentation

## [0.2.4]

- fixed attaching sources for IDEA Ultimate and bundled plugins

## [0.2.3]

- fixed compilation for multi-module layout

## [0.2.2]

- added `runIde` task. `runIdea` is deprecated now (fixes [#169](../../issues/169))
- fixed kotlin forms instrumentation (fixes [#171](../../issues/171))
- fixed filtering out all resources of dependent plugins (fixes [#172](../../issues/172))
- fixed intellij.systemProperties extension (fixes [#173](../../issues/173))

## [0.2.1]

- added Rider support (fixes [#167](../../issues/167))
- fix unresolved builtin plugins on case-insensitive file systems

## [0.2.0]

- result artifact format is changed: now it's always a ZIP archive even if plugin has no extra dependencies. *Note that this may change classloading (
  see [#170](../../issues/170))*
- added an ability to use local IDE installation for compiling
- result zip archive is added to `archives` configuration, built-in `assemble` task now builds the plugin distribution
- added JPS-type for intellij dependency (fixes [#106](../../issues/106))
- patchXml action is reimplemented, now it's possible to freely customize input files, destination directory, since/until builds, plugin description and version
- publishTask is reimplemented, now it's possible to set several channels to upload (fixes [#117](../../issues/117))
- 
    - it's possible to reuse reimplemented tasks in client's code
    - it's allowed to run tasks without plugin.xml
    - tasks are configured before project evaluation, `project.afterEvaluate` is not require anymore
- fix incremental compiling after instrumenting code (fixes [#116](../../issues/116))
- added `intellij.ideaDependencyCachePath` option (fixes [#127](../../issues/127))
- `project()` reference can be used as a plugin-dependency (fixes [#17](../../issues/17))
- fix attaching sources of builtin plugins (fixes [#153](../../issues/153))

## [0.1.10]

- Do not override plugins directory content (temporary fix of [#17](../../issues/17))

## [0.1.9]

- Added default configuration to ivy-repositories (fixes [#114](../../issues/114))

## [0.1.6]

- External plugin directories are placed in compile classpath so IDEA code insight is better for them now (fixes [#105](../../issues/105))

## [0.1.4]

- Fix incremental compilation on changing `intellij.version` (fixes [#67](../../issues/67))

## [0.1.0]

- Support external plugin dependencies

## [0.0.41]

- Fix Kotlin forms instrumentation ([#73](../../issues/73))

## [0.0.39]

- Allow making single-build plugin distributions (fixes [#64](../../issues/64))

## [0.0.37]

- Exclude kotlin dependencies if needed (fixes [#57](../../issues/57))

## [0.0.35]

- Disable automatic updates check in debug IDEA (fixes [#46](../../issues/46))

## [0.0.34]

- Support local IDE installation as a target application of `runIdea` task

## [0.0.33]

- Attach community sources to ultimate IntelliJ artifact (fixes [#37](../../issues/37))
- New extension for passing system properties to `runIdea` task (fixes [#18](../../issues/18))

## [0.0.32]

- Support compilation in IDEA 13.1 (fixes [#28](../../issues/28))

## [0.0.30]

- Fixed broken `runIdea` task

## [0.0.29]

- `cleanTest` task clean `system-test` and `config-test` directories (fixes [#13](../../issues/13))
- Do not override plugins which were installed in debug IDEA (fixes [#24](../../issues/24))

## [0.0.28]

- `RunIdeaTask` is extensible (fixes [#23](../../issues/23))
- Fix xml parsing exception (fixes [#25](../../issues/25))

## [0.0.27]

- Disabled custom class loader in tests (fixes [#21](../../issues/21))

## [0.0.25]

- Do not patch version tag if `project.version` property is not specified (fixes [#11](../../issues/11))

## [0.0.21]

- IntelliJ-specific jars are attached as compile dependency (fixes [#5](../../issues/5))

## [0.0.10]

- Support for attaching IntelliJ sources in IDEA

[next]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0-rc1...HEAD
[2.0.0-rc1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0-beta9...v2.0.0-rc1
[2.0.0-beta9]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0-beta8...v2.0.0-beta9
[2.0.0-beta8]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0-beta7...v2.0.0-beta8
[2.0.0-beta7]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0-beta6...v2.0.0-beta7
[2.0.0-beta6]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0-beta5...v2.0.0-beta6
[2.0.0-beta5]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0-beta4...v2.0.0-beta5
[2.0.0-beta4]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0-beta3...v2.0.0-beta4
[2.0.0-beta3]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0-beta2...v2.0.0-beta3
[2.0.0-beta2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0-beta1...v2.0.0-beta2
[2.0.0-beta1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.17.4...v2.0.0-beta1
[1.17.4]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.17.3...v1.17.4
[1.17.3]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.17.2...v1.17.3
[1.17.2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.17.1...v1.17.2
[1.17.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.17.0...v1.17.1
[1.17.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.16.1...v1.17.0
[1.16.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.16.0...v1.16.1
[1.16.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.15.0...v1.16.0
[1.15.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.14.2...v1.15.0
[1.14.2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.14.1...v1.14.2
[1.14.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.14.0...v1.14.1
[1.14.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.13.3...v1.14.0
[1.13.3]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.13.2...v1.13.3
[1.13.2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.13.1...v1.13.2
[1.13.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.13.0...v1.13.1
[1.13.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.12.0...v1.13.0
[1.12.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.11.0...v1.12.0
[1.11.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.10.2...v1.11.0
[1.10.2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.10.1...v1.10.2
[1.10.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.10.0...v1.10.1
[1.10.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.9.0...v1.10.0
[1.9.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.8.1...v1.9.0
[1.8.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.8.0...v1.8.1
[1.8.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.6.0...v1.7.0
[1.6.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.5.3...v1.6.0
[1.5.3]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.5.2...v1.5.3
[1.5.2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.5.1...v1.5.2
[1.5.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.5.0...v1.5.1
[1.5.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.3.1...v1.4.0
[1.3.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.2.1...v1.3.0
[1.2.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.1.6...v1.2.0
[1.1.6]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.1.5...v1.1.6
[1.1.5]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.1.4...v1.1.5
[1.1.4]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.1.3...v1.1.4
[1.1.3]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v1.0.0...v1.1.2
[1.0.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.7.3...v1.0.0
[0.7.3]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.7.2...v0.7.3
[0.7.2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.7.1...v0.7.2
[0.7.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.6.5...v0.7.0
[0.6.5]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.6.4...v0.6.5
[0.6.4]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.6.3...v0.6.4
[0.6.3]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.6.2...v0.6.3
[0.6.2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.6.1...v0.6.2
[0.6.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.5.1...v0.6.0
[0.5.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.26...v0.5.0
[0.4.26]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.25...v0.4.26
[0.4.25]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.24...v0.4.25
[0.4.24]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.23...v0.4.24
[0.4.23]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.22...v0.4.23
[0.4.22]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.21...v0.4.22
[0.4.21]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.20...v0.4.21
[0.4.20]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.19...v0.4.20
[0.4.19]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.18...v0.4.19
[0.4.18]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.17...v0.4.18
[0.4.17]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.16...v0.4.17
[0.4.16]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.15...v0.4.16
[0.4.15]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.14...v0.4.15
[0.4.14]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.13...v0.4.14
[0.4.13]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.12...v0.4.13
[0.4.12]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.11...v0.4.12
[0.4.11]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.10...v0.4.11
[0.4.10]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.9...v0.4.10
[0.4.9]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.8...v0.4.9
[0.4.8]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.7...v0.4.8
[0.4.7]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.6...v0.4.7
[0.4.6]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.5...v0.4.6
[0.4.5]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.4...v0.4.5
[0.4.4]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.3...v0.4.4
[0.4.3]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.2...v0.4.3
[0.4.2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.3.12...v0.4.0
[0.3.12]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.3.11...v0.3.12
[0.3.11]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.3.10...v0.3.11
[0.3.10]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.3.7...v0.3.10
[0.3.7]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.3.6...v0.3.7
[0.3.6]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.3.5...v0.3.6
[0.3.5]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.3.4...v0.3.5
[0.3.4]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.3.3...v0.3.4
[0.3.3]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.3.2...v0.3.3
[0.3.2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.20...v0.3.0
[0.2.20]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.19...v0.2.20
[0.2.19]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.18...v0.2.19
[0.2.18]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.17...v0.2.18
[0.2.17]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.16...v0.2.17
[0.2.16]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.15...v0.2.16
[0.2.15]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.14...v0.2.15
[0.2.14]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.13...v0.2.14
[0.2.13]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.12...v0.2.13
[0.2.12]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.11...v0.2.12
[0.2.11]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.10...v0.2.11
[0.2.10]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.9...v0.2.10
[0.2.9]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.8...v0.2.9
[0.2.8]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.7...v0.2.8
[0.2.7]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.6...v0.2.7
[0.2.6]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.5...v0.2.6
[0.2.5]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.4...v0.2.5
[0.2.4]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.3...v0.2.4
[0.2.3]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.2...v0.2.3
[0.2.2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.1...v0.2.2
[0.2.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.1.10...v0.2.0
[0.1.10]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.1.9...v0.1.10
[0.1.9]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.1.6...v0.1.9
[0.1.6]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.1.4...v0.1.6
[0.1.4]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.1.0...v0.1.4
[0.1.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.41...v0.1.0
[0.0.41]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.39...v0.0.41
[0.0.39]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.37...v0.0.39
[0.0.37]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.35...v0.0.37
[0.0.35]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.34...v0.0.35
[0.0.34]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.33...v0.0.34
[0.0.33]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.32...v0.0.33
[0.0.32]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.30...v0.0.32
[0.0.30]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.29...v0.0.30
[0.0.29]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.28...v0.0.29
[0.0.28]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.27...v0.0.28
[0.0.27]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.25...v0.0.27
[0.0.25]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.21...v0.0.25
[0.0.21]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v0.0.10...v0.0.21
[0.0.10]: https://github.com/JetBrains/intellij-platform-gradle-plugin/commits/v0.0.10
