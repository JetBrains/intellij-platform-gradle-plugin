# Changelog

## [next]

### Added

- Add `compatiblePlugin(id)`/`compatiblePlugins(ids)` dependency helper for resolving plugins from JetBrains Marketplace in the latest compatible versions.
- Add `TestFrameworkType.Plugin.CSS` and `TestFrameworkType.Plugin.XML` to support test development requiring XML or CSS language.
- Warn when Plugin Verifier is about to run verification against more than five IDEs.
- Make the `printBundledPlugins` task print bundled plugin names next to IDs. JetBrains/intellij-platform-gradle-plugin#1674

### Changed

- Deprecate Aqua (QA) as a target IntelliJ Platform
- Print requested IntelliJ Platform when throwing exception about unresolved dependency.
- Make `IntelliJPlatformDependenciesHelper` aware of custom IntelliJ Platform when used within custom tasks.
- Move the Coroutines JavaAgent lock file into module's build directory
- Skip creating the Coroutines JavaAgent for modules
- Remove the default `untilBuild` value
- Drop JPS dependencies shadowing

### Fixed

- Add test-related sandbox configurations and runtime fixes
- Set the required `extracted` and `collected` attributes for custom test classpath configurations
- Set the repository name and fix the `action` application in `createCustomPluginRepository`
- Refactor build service registration to use `registerClassLoaderScopedBuildService` to avoid issues caused because of different classpath in the project. JetBrains/intellij-platform-gradle-plugin#1919
- Use proper IntelliJ Platform when setting up custom tests runtime

## [2.5.0] - 2025-04-01

### Added

- Introduce configurations for IntelliJ Platform test plugins, dependencies, and bundled modules along with `testPlugin`, `testBundledPlugin`, and `testBundledModule` dependency helpers
- Support different `kotlinx.coroutines` JavaAgent FQNs by checking for the class presence in the IntelliJ Platform classpath

### Fixed

- Do not fail when JBR dependency cannot be resolved, and proceed with JRE resolution using other available predictions
- Performance improvement: memoize the `ProductInfoPathResolver` resolution.

## [2.4.0] - 2025-03-20

### Added

- Register the `TestIdeUiTask` for Starter purposes.
- Introduce a custom test classpath configuration for custom `TestableAware`-like tasks
- Add `AddDefaultIntelliJPlatformDependencies` property to control if default dependencies (`com.intellij` and `intellij.rider`) are added automatically with IntelliJ Platform
- Use the exact IntelliJ Platform version to resolve platform dependencies when targeting IntelliJ Platform from the nightly channel.
- Introduce the `intellijPlatformTestRuntimeClasspath` configuration
- Automatically load the `com.intellij.clion` into the CLion classpath
- Introduce the `ExtractorService` as a reusable tool for custom IntelliJ Platform extraction flow cases 

### Changed

- Stop shadowing Gradle plugin dependencies, manually repack only JPS Module
- Load the `com.intellij` module by default when creating the IntelliJ Platform dependency
- Load the `com.intellij` module with its optional dependencies for tests classpath as a cleaner fix for [IJPL-180516](https://youtrack.jetbrains.com/issue/IJPL-180516/Gradle-tests-fail-without-transitive-modules-jars-of-com.intellij-in-classpath)
- Store `localPlatformArtifacts` Ivy XML files within the version-based subdirectory
- Deprecate Writerside (WRS) as a target IntelliJ Platform
- Use Plugin Verifier libraries to resolve the bundled plugin classpath

### Fixed

- Performance improvement: memoize the `JavaRuntimePathResolver` resolution.
- Performance improvement: do not resolve JetBrains IDEs/Android Studio release URLs in the configuration phase.
- Performance improvement: cache the IntelliJ Platform instances parsed with the Plugin Verifier libraries
- Tests classpath: do not blindly include all plugin's `lib/**/*.jar`
- Tests classpath: do not load all bundled plugins and modules into the classpath
- Classpath: correctly resolve all necessary modules for bundled plugins and modules
- Avoid creating tasks eagerly and use `configureEach(configuration)` instead. JetBrains/intellij-platform-gradle-plugin#1901

## [2.3.0] - 2025-02-27

### Added

- Warn that using Rider as a target IntelliJ Platform with `useInstaller = true` is currently not supported, please set `useInstaller = false` instead. JetBrains/intellij-platform-gradle-plugin#1852
- Add `ide(type: Provider<*>, version: Provider<String>, useInstaller: Provider<Boolean>)` overload to the `pluginVerification.ides` block.
- Warn that since the IntelliJ Platform version `2025.1` (build `251`), the required Kotlin version is `2.0.0` or higher.
- Load the `com.intellij` bundled module by default for all IntelliJ Platform types
- Warn when the "until-build" property is specified for IntelliJ Platform version 243 or higher, as it is ignored

### Fixed

- Fixed the broken path resolution in the `bundledLibrary` helper and the `TestFrameworkType.Bundled` test framework
- Fixed configuring dependencies on plugins with required content modules. JetBrains/intellij-platform-gradle-plugin#1883
- Fixed dependencies between submodules. JetBrains/intellij-platform-gradle-plugin#1854

## [2.2.1] - 2025-01-21

### Added

- Introduce LSP API Test Framework entry as `TestFrameworkType.Plugin.LSP`

### Changed

- Better resolve the plugin dependency path with nested directories in the plugin archive.

### Fixed

- Local IntelliJ Platform wasn't handled with the `CollectorTransformer` due to the missing attributes applied to the `directory` archive.
- Plugin Verifier: suppress plugin problems for bundled or third-party plugins
- Plugin Verifier: when creating an IDE, silently skip missing layout entries
- Custom plugin repository adjustments

## [2.2.0] - 2024-12-06

### Added

- Introduce `intellijPlatformClasspath` configuration to allow retrieving the processed IntelliJ Platform and plugins dependencies. 
- Warn if trying to refer to a bundled plugin via `intellijPlatform.plugins` instead of required `intellijPlatform.bundledPlugin`.
- Set `ide.native.launcher=false` for `RunnableIdeAware` tasks to prevent from showing the `The IDE seems to be launched with a script launcher` warning notification.
- Add bundledPlugins with transitive dependencies to the test runtime classpath.
- Set `intellij.testFramework.rethrow.logged.errors=true` to make logged errors fails test immediately. JetBrains/intellij-platform-gradle-plugin#1828

### Changed

- Move `localPlatformArtifacts()` to the top of the `defaultRepositories()` list. 
- Make IntelliJ Platform dependencies available to `testImplementation` by default to avoid mutating tests classpath
- For running testing, use test sandbox instead of compiled classes from `build/classes` directory 
- Updated the `publishPlugin()` task to use IDE Services’ new `POST /api/plugins` API when configured to publish to the IDE Services plugin repository, replacing the deprecated `POST /api/ij-plugins/upload` endpoint
- Rewrite bundled plugins/modules resolution by using the IntelliJ Structure library.
- Update minimal supported Gradle version to `8.5`

### Fixed

- Make the `testRuntimeClasspath` configuration request instrumented and composed jars of imported modules when running tests
- Adjust local artifact definition in Ivy XML files to satisfy Gradle dependency locking. JetBrains/intellij-platform-gradle-plugin#1778
- Add the missing `org.jetbrains.kotlin.platform.type=jvm` attribute to the `intellijPlatformRuntimeClasspath` configuration manually as it is not inherited from the `runtimeClasspath`.
- `Could not generate a decorated class for type PluginArtifactRepository.` when creating a custom plugin repository.
- Generation of duplicate files in `.intellijPlatform/localPlatformArtifacts` with different version numbers.
- Gradle's `api` & `compileOnlyApi` configurations created by its _java-library_ plugin don't work, and transitive implementation scope dependencies get exposed, when this plugin is used. JetBrains/intellij-platform-gradle-plugin#1799
- Incorrect transitive dependencies calculation for bundled modules. JetBrains/intellij-platform-gradle-plugin#1791
- Fixed `IndexOutOfBound` exception while running tests from Gradle.
- Building the searchable options: `Unable to create shared archive file $IDE_CACHE_DIR/pycharm243.18137.19.jsa: (No such file or directory).`
- Compatibility with Gradle dependency verification. Previously it was failing with `Failed to create MD5 hash for file`.
- Rework how the IDEs from Plugin Verification are resolved. JetBrains/intellij-platform-gradle-plugin#1784
- Exclude `kotlin-stdlib` and `kotlinx-coroutines` transitive dependencies in various variants from IntelliJ Platform dependencies. JetBrains/intellij-platform-gradle-plugin#1817
- Can't find `performanceTesting.jar` when building against Android Studio 242+. JetBrains/intellij-platform-gradle-plugin#1738
- Custom `runIde` task do not find the right runtime, if `useInstaller` is `false`. JetBrains/intellij-platform-gradle-plugin#1827
- PluginVerifier doesn't honor gradle offline mode. JetBrains/intellij-platform-gradle-plugin#1820
- Make  `intellij.rider` bundled module automatically available for Rider. JetBrains/intellij-platform-gradle-plugin#1774
- Bump vulnerable `com.squareup.okio:okio:1.17.2` to `1.17.6`. JetBrains/intellij-platform-gradle-plugin#1795

## [2.1.0]

### Added

- Added `PrepareSandboxTask.pluginName` for easier accessing of the plugin directory name
- Allow for using non-installer IDEs for plugin verification JetBrains/intellij-platform-gradle-plugin#1715
- Added `bundledModule()` dependency extension helpers
- Detect and warn about the `kotlinx-coroutines` library in transitive dependencies
- Introduce caching when creating dependencies 

### Changed

- Add IntelliJ Platform v2 product modules to the test classpath
- Invalidate local dependency artifacts XML files running

### Fixed

- Fixed caching for `IntelliJPlatformArgumentProvider.coroutinesJavaAgentFile`.
- `intellijPlatform.pluginConfiguration.description` appends content instead of replacing it JetBrains/intellij-platform-gradle-plugin#1744
- The `disabledPlugins.txt` file is not updated when disabling bundled plugins JetBrains/intellij-platform-gradle-plugin#1745
- Plugin sandbox created by `IntelliJPlatformTestingExtension` is not correct for `localPlugin(org.gradle.api.artifacts.ProjectDependency)` JetBrains/intellij-platform-gradle-plugin#1743  
- Better resolving the JVM variant of the `io.github.pdvrieze.xmlutil` dependency JetBrains/intellij-platform-gradle-plugin#1741
- Remove the inclusion of all IntelliJ Platform v2 bundled modules to the classpath JetBrains/intellij-platform-gradle-plugin#1761
- Duplicate bundled template error in tests JetBrains/intellij-platform-gradle-plugin#1755
- Fixed the IDE problem when submodule jars appear as External Libraries
- Fixed `java.util.ConcurrentModificationException` on Gradle sync caused by the `pluginVerification` configuration JetBrains/intellij-platform-gradle-plugin#1714
- Fixed Configuration Cache issues related to the `intellijPlatform.buildSearchableOptions` flag

## [2.0.1] - 2024-08-08

### Changed

- Don't register the `testIdeUi` task by default
- Add `DisableCachingByDefault` to `PrepareSandboxTask` JetBrains/intellij-platform-gradle-plugin#1721
- Make the `prepareSandbox` task always run whenever any of the sandbox directories is missing JetBrains/intellij-platform-gradle-plugin#1730

### Fixed

- Fixed "No IDE resolved for verification with the IntelliJ Plugin Verifier" when `untilBuild` is an empty string JetBrains/intellij-platform-gradle-plugin#1717
- Dependency resolution fails if a repository with `exclusiveContent` rules is added twice – use `content` inclusive rule instead JetBrains/intellij-platform-gradle-plugin#1728
- Apply `composedJar` library elements attribute to `testRuntimeClasspath` configuration
- When adding new IDEs for Plugin Verifier, do not mutate existing `intellijPluginVerifierIdes_X` configurations if present
- Respect the `sandboxDirectory` property when configuring custom tasks with `intellijPlatformTesting` extension JetBrains/intellij-platform-gradle-plugin#1723

## [2.0.0] - 2024-07-30

**IntelliJ Platform Gradle Plugin 2.0 is out!**

_Read the [full blog post](https://blog.jetbrains.com/platform/2024/07/intellij-platform-gradle-plugin-2-0/)._

Version 2.0 of the IntelliJ Platform Gradle Plugin is now available! Previously called the Gradle IntelliJ Plugin, this updated plugin for the Gradle build system simplifies the configuration of environments for building, testing, verifying, and publishing plugins for IntelliJ-based IDEs. Redesigned and rewritten, it addresses community-reported issues and feature requests from previous versions.

To start using version 2.0 of the IntelliJ Platform Gradle Plugin,
visit the documentation pages at [IntelliJ Platform SDK | Tooling | IntelliJ Platform Gradle Plugin 2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html),
where you will find a proper introduction to all the new features and changes,
a [migration guide](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-migration.html),
and examples of various configurations you may be interested in.

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
- Avoid recalculating the `IvyModule` for bundled plugin/module if already written to the Ivy XML file

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
- Dependency extension: `platformDependency(groupId, artifactIt)` and `testPlatformDependency(groupId, artifactIt)` for adding dependencies on artifacts published to the IntelliJ Maven Repository
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
- Replace a base archive file of the `Jar` task with `ComposedJarTask` archive file in all configuration artifact sets
- Redundant whitespace when parsing plugin dependency IDs
- Plugin Verifier: introduce partial configuration for resolving IntelliJ Platform dependencies with the same coordinates but different versions

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
- Introduce `VerifyPluginProjectConfigurationTask.hasModulePlugin` to exclude modules using `org.jetbrains.intellij.platform.module` sub-plugin from `plugin.xml` checks.
- Better error handling in dependency helpers when missing values
- Introduce `GenerateManifestTask` for generating `MANIFEST.MF` file
- Introduce `ComposedJarTask` to compose and pick the final jar archive
- Introduce `intellijPlatform.pluginModule(Dependency)` dependency helper to compose a single jar combined of multiple modules

### Fixed

- Avoid leaking internal properties from `intellijPlatform` extensions
- Fixed custom tasks suffixing
- Fixed: Task `:test` uses this output of task `:prepareSandbox` without declaring an explicit or implicit dependency JetBrains/intellij-platform-gradle-plugin#1609
- ExtractorTransformer: Exclude only `Applications` symlink
- SandboxAware: inherit sandbox directory from the producer
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
- Fixed resolving Android Studio releases URL for Windows JetBrains/intellij-platform-gradle-plugin#1551
- Fixed repository type classification for Rider RC builds JetBrains/intellij-platform-gradle-plugin#1579

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
- PatchPluginXmlTask: Wrap the content passed to `<change-notes>` and `<description>` elements with `<![CDATA[ ... ]]>` JetBrains/intellij-platform-gradle-plugin#1498

### Changed

- New project name: IntelliJ Platform Gradle Plugin
- New project ID: `org.jetbrains.intellij.platform`
- New Maven coordinates: `org.jetbrains.intellij.platform:intellij-platform-gradle-plugin`
- Move classes under the new package: `org.jetbrains.intellij.platform.gradle`
- Update minimal supported Gradle version to `8.2`

### Fixed

- Do not discover `idea.platform.prefix` by scanning shell scripts for `223+` JetBrains/intellij-platform-gradle-plugin#1525
- MemoizedProvider incompatible with Gradle 8.6 RC1 JetBrains/intellij-platform-gradle-plugin#1517
- Tasks `:classpathIndexCleanup` and `:compileTestKotlin` don't have a declared dependency causing build to fail JetBrains/intellij-platform-gradle-plugin#1515
- ListProductsReleases empty for `2023.3` JetBrains/intellij-platform-gradle-plugin#1505

## [1.16.1] - 2023-12-01

### Added

- Make RustRover (`RR` type) available for resolving as SDK.

### Fixed

- Attach IntelliJ SDK sources before LSP API sources JetBrains/intellij-platform-gradle-plugin#1490
- Fixed `RunPluginVerifierTask.FailureLevel.NOT_DYNAMIC` presence check JetBrains/intellij-platform-gradle-plugin#1485

## [1.16.0] - 2023-10-06

### Added

- Configure all tasks that extend task classes instead of just those created by the plugin
- Make `JbrResolver` prefer Gradle `javaToolchains` by `JetBrains` vendor, if already available.
- Support for Kotlin Coroutines debugging
- Detect and warn if a project adds an explicit dependency on the Kotlin Coroutines library
- `RunPluginVerifierTask`: new `runPluginVerifier.verificationReportsFormats` property to control verifier output formats
- `RunPluginVerifierTask`: new `runPluginVerifier.ignoredProblems` property to include a file with a list of problems to be ignored in a report
- `RunPluginVerifierTask`: new `runPluginVerifier.freeArgs` property to let pass to the IntelliJ Plugin Verifier custom arguments

### Fixed

- `NoClassDefFoundError: org/gradle/api/publish/ivy/internal/publication/DefaultIvyPublicationIdentity` in Gradle 8.4 JetBrains/intellij-platform-gradle-plugin#1469
- Misleading message about Kotlin API version JetBrains/intellij-platform-gradle-plugin#1463

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
- Introduced `DownloadAndroidStudioProductReleasesXmlTask.releasesUrl` and `DownloadIdeaProductReleasesXmlTask.releasesUrl` properties JetBrains/intellij-platform-gradle-plugin#1418

### Changed

- Renamed `ListProductsReleasesTask.productsReleasesUpdateFiles` property to `ListProductsReleasesTask.ideaProductReleasesUpdateFiles`

### Removed

- Removed `ListProductsReleasesTask.updatePaths` property

## [1.14.2] - 2023-06-26

### Added

- Create a date-based lock file to limit daily update checks for the Gradle IntelliJ Plugin.

### Fixed

- Handle the `Could not HEAD 'https://www.jetbrains.com/updates/updates.xml'` gracefully when running `downloadIdeaProductReleasesXml` with no Internet connection
- Improved checking if `Provider` holds non-empty value
- Fixed calculation of JVM arguments for running tests JetBrains/intellij-platform-gradle-plugin#1360
- Introduce CommandLineArgumentProviders for better management of JVM arguments and avoiding passing absolute paths to support Gradle Build Cache JetBrains/intellij-platform-gradle-plugin#1376
- Replace deprecated `JavaPluginConvention` usages with `JavaPluginExtension` for Gradle 8.2 and 9.x compatibility JetBrains/intellij-platform-gradle-plugin#1413
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

- Resolving Android Studio JNA libraries on macOS JetBrains/intellij-platform-gradle-plugin#1353
- Fixed "Must not use `executable` property on `Test` together with `javaLauncher` property" for Gradle `7.x` JetBrains/intellij-platform-gradle-plugin#1358
- Task `:listProductsReleases` creates empty file due to `MalformedByteSequenceException` JetBrains/intellij-platform-gradle-plugin#1389
- Make `RunIdeBase.pluginsDir` a `@Classpath` input, fixes cacheability of `buildSearchableOptions` JetBrains/intellij-platform-gradle-plugin#1370
- Fixed `JarSearchableOptionsTask` cacheability JetBrains/intellij-platform-gradle-plugin#1375

### Changed

- Set minimum supported Gradle version from `7.3` to `7.6`

## [1.13.3] - 2023-03-28

### Added

- Run tests using JBR JetBrains/intellij-platform-gradle-plugin#473
- Introduce `verifyPluginSignature` task for verification signed plugin archive produced by `signPlugin` task
- Provide KDoc documentation for all tasks and extensions JetBrains/intellij-platform-gradle-plugin#1345

### Fixed

- Add instrumented classes (sources and tests) to the tests classpath before tests execution JetBrains/intellij-platform-gradle-plugin#1332
- Fixed `NSDictionary` helper methods to return `null` instead of `"null"` — causing "Resource not found: /idea/nullApplicationInfo.xml" JetBrains/intellij-platform-gradle-plugin#1348

## [1.13.2] - 2023-03-10

### Fixed

- Add instrumented classes (sources and tests) to the tests classpath JetBrains/intellij-platform-gradle-plugin#1332

## [1.13.1] - 2023-03-02

### Added

- Provide `idea.log.path` system property for `RunIde`-based tasks and tests

### Fixed

- Unsupported JVM architecture was selected for running Gradle tasks: `x86_64` JetBrains/intellij-platform-gradle-plugin#1317
- Instrumentation ignores `intellij.instrumentCode = false` JetBrains/intellij-platform-gradle-plugin#1310
- `NoClassDefFoundError: org/jetbrains/kotlin/konan/file/FileKt` when running `signPlugin` task on Gradle lower than 8.0 JetBrains/intellij-platform-gradle-plugin#1319
- `taskdef class com.intellij.ant.InstrumentIdeaExtensions cannot be found` when running instrumentation on Android Studio JetBrains/intellij-platform-gradle-plugin#1288
- JVM arguments mangled since `1.10` resulting in `ClassNotFoundException` for `PathClassLoader` JetBrains/intellij-platform-gradle-plugin#1311
- Add missing compiled classes to the instrumentation task classpath
- Mark `RunPluginVerifierTask.FailureLevel.ALL` and `RunPluginVerifierTask.FailureLevel.NONE` with `@JvmField` annotation JetBrains/intellij-platform-gradle-plugin#1323

## [1.13.0] - 2023-02-10

### Added

- Support for Gradle `8.0`
- Introduced the `initializeIntelliJPlugin` task for executing plugin initialization actions, like `checkPluginVersion`
- `instrumentJar` task to produce an independent jar file with instrumented classes
- `instrumentedJar` configuration for multi-modules projects
- Publish a plugin marker to the Maven Snapshot Repository

### Fixed

- Don't enforce the Kotlin version of the project by using `compileOnly` instead of `api` when declaring the `org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0` dependency JetBrains/intellij-platform-gradle-plugin#1276
- Instrumentation: fixed configuration cache support, dropped the `postInstrumentCode` and `postInstrumentTestCode` tasks JetBrains/intellij-platform-gradle-plugin#1283

### Breaking Changes

- It is required to use the `instrumentedJar` configuration when referring submodules in a multi-modules project, like `dependencies { implementation(project(":submodule", "instrumentedJar")) }`
- Rename `IntelliJInstrumentCodeTask` to `InstrumentCodeTask`

## [1.12.0] - 2023-01-13

### Added

- Extract ZIP Signer CLI tool downloading as `downloadZipSigner` task
- Allow for passing `signPlugin.privateKey` and `signPlugin.certificateChain` as base64-encoded value

### Changed

- Download IDEs used by the Plugin Verifier in the task execution phase.

### Fixed

- Added missing incremental task annotation properties and cacheability annotations JetBrains/intellij-platform-gradle-plugin#1258
- Make `listBundledPlugins` not printing output as `printBundledPlugins` does that
- Fixed `taskdef class com.intellij.ant.InstrumentIdeaExtensions cannot be found` JetBrains/intellij-platform-gradle-plugin#1259
- Don't warn about unexpected instrumentation task name JetBrains/intellij-platform-gradle-plugin#1214

## [1.11.0] - 2022-12-17

### Added

- `printProductsReleases` task to print the result of the `listProductsReleases` task
- `printBundledPlugins` task to print the result of the `listBundledPlugins` task
- `runIde.jbrArch` and `runPluginVerifier.jbrArch` properties for the explicit JBR architecture specification

### Changed

- `custom(String)` helper of `intellij.pluginRepositories` configuration requires now passing a direct URL to the `updatePlugins.xml` file JetBrains/intellij-platform-gradle-plugin#1252
- `listProductsReleases` task doesn't print output anymore
- `listBundledPlugins` task doesn't print output anymore
- Set minimum supported Gradle version to `7.3`

### Fixed

- Replace `Contents/Contents` part within JVM arguments to a single `Contents` – happens with macOS distribution
- `--offline` prevents from using JBR even if it is already downloaded JetBrains/intellij-platform-gradle-plugin#1251

## [1.10.2] - 2022-12-16

### Changed

- Revert the minimum supported Gradle version to `6.8`

## [1.10.1] - 2022-12-08

### Changed

- Set minimum supported Gradle version from `6.8` to `7.1`

### Fixed

- Fixed "Error: Could not find or load main class" when using older SDK versions
- Fix launch information could not be found for macOS. JetBrains/intellij-platform-gradle-plugin#1230
- Fixed "Cannot change dependencies of dependency configuration ... after it has been included in dependency resolution" JetBrains/intellij-platform-gradle-plugin#1209

## [1.10.0] - 2022-11-17

### Added

- Set `IDEA_PLUGIN_SANDBOX_MODE` to `true` for `runIde`-based tasks
- The `listBundledPlugins` task for listing IDs of plugins bundled within the currently targeted IDE
- Make sure `1.10.0` is higher than `1.10.0-SNAPSHOT` in version check JetBrains/intellij-platform-gradle-plugin#1155

### Fixed

- Invalidate instrumented classes bound to forms if GUI changed [IDEA-298989](https://youtrack.jetbrains.com/issue/IDEA-298989/Duplicate-method-name-getFont)
- Revert pushing project resource directories to the end of the classpath in the test task context. (JetBrains/intellij-platform-gradle-plugin#1101)
- Avoid unnecessary task configuration during the Gradle configuration phase JetBrains/intellij-platform-gradle-plugin#1110
- Replace internal Gradle ConventionTask with DefaultTask JetBrains/intellij-platform-gradle-plugin#1115
- Plugin Verifier cache directory now follows XDG cache standards JetBrains/intellij-platform-gradle-plugin#1119
- Migrate most of the Gradle API in `IntelliJPlugin.kt` to use the Gradle Kotlin DSL extensions JetBrains/intellij-platform-gradle-plugin#1117
- Support `runIde.jbrVersion` in `17.0.4.1-b653.1` format JetBrains/intellij-platform-gradle-plugin#1172
- Plugin dependencies not resolved in multi-module project JetBrains/intellij-platform-gradle-plugin#1196
- Finalize instrumentation with `classpathIndexCleanup` run to remove `classpath.index` file which breaks incremental build
- Misleading message about Kotlin language version JetBrains/intellij-platform-gradle-plugin#1156
- Fix launch information could not be found for macOS.JetBrains/intellij-platform-gradle-plugin#1230

### Changed

- Set minimum supported Gradle version from `6.7.1` to `6.8`
- Use information from `product-info.json` for running `223+`

## [1.9.0] - 2022-09-02

### Added

- Configure classpath for run-based tasks using `package-info.json` provided with IntelliJ SDK 2022.3+
- The [`verifyPluginConfiguration`](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-verifypluginconfiguration) task for validating the plugin project configuration.
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
- Synchronize `OpenedPackages` list with the [latest version](https://raw.githubusercontent.com/JetBrains/intellij-community/master/plugins/devkit/devkit-core/src/run/OpenedPackages.txt) available.
- Make PhpStorm (`PS` type) available for resolving as SDK.

### Changed

- Rearrange classpath to put `idea` and `ideaPlugins` dependencies in the right order.
- Rename plugin configurations to move injected dependencies to the end of the classpath. JetBrains/intellij-platform-gradle-plugin#1060

### Removed

- Remove the `DEPENDENCY_FIRST` resolution strategy set by default along with its `BuildFeature.USE_DEPENDENCY_FIRST_RESOLUTION_STRATEGY` flag.
- Remove setting of the `java.system.class.loader` property from tests configuration.

### Fixed

- Exclude non-jar files from the classpath JetBrains/intellij-platform-gradle-plugin#1009
- Jacoco reports false 0% test coverage JetBrains/intellij-platform-gradle-plugin#1065
- Unable to load JUnit4 runner to calculate Ignored test cases JetBrains/intellij-platform-gradle-plugin#1033

## [1.7.0] - 2022-07-08

### Added

- Automatically detect bundled sources in plugin dependency JetBrains/intellij-platform-gradle-plugin#786
- Automatically detect plugin dependency sources provided in the IDE distribution JetBrains/intellij-platform-gradle-plugin#207
- Throw an error when `intellij.version` is missing JetBrains/intellij-platform-gradle-plugin#1010
- Set `ResolutionStrategy.SortOrder.DEPENDENCY_FIRST` for `compileClasspath` and `testCompileClasspath` configurations JetBrains/intellij-platform-gradle-plugin#656
- Added `useDependencyFirstResolutionStrategy` feature flag. See [Feature Flags](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#build-features).
- Ensure `classpath.index` is not bundled in the JAR file
- Warn about no settings provided by the plugin when running `buildSearchableOptions` and suggest [disabling the task](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-options). JetBrains/intellij-platform-gradle-plugin#1024
- Warn about paid plugin running `buildSearchableOptions` and suggest [disabling the task](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-options). JetBrains/intellij-platform-gradle-plugin#1025
- IDE dependencies are added to the `compileOnly` classpath for test fixtures if the `java-test-fixtures` plugin is applied JetBrains/intellij-platform-gradle-plugin#1028
- `classpathIndexCleanup` task is added to remove `classpath.index` files created by `PathClassLoader` JetBrains/intellij-platform-gradle-plugin#1039
- Improve Plugin Verifier error messages JetBrains/intellij-platform-gradle-plugin#1040
- Added `FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES` to the Plugin Verifier task
- Support for JetBrains Runtime 2022.2 directories layout JetBrains/intellij-platform-gradle-plugin#1016

### Changed

- Set minimum supported Gradle version from `6.7` to `6.7.1`
- Resolve dependencies using repositories in the following order: project custom repositories (if any), plugin custom repositories, common repositories (like Maven Central)
- Add an executable flag for `Rider.Backend` native launchers in `IdeaDependencyManager#resetExecutablePermissions` [RIDER-59978](https://youtrack.jetbrains.com/issue/RIDER-59978)
- Remove Gradle dependencies constraints as transitive dependencies don't point to vulnerabilities anymore JetBrains/intellij-platform-gradle-plugin#999

### Fixed

- Fixed broken instrumentation when the custom sources directory is set JetBrains/intellij-platform-gradle-plugin#1004
- Fixed `java.nio.file.FileAlreadyExistsException` when instrumenting code JetBrains/intellij-platform-gradle-plugin#998
- Fixed `Execution optimizations have been disabled for task ':jar' to ensure correctness` JetBrains/intellij-platform-gradle-plugin#1000
- Fixed JaCoCo `Can't add different class with same name` exception when using code instrumentation JetBrains/intellij-platform-gradle-plugin#1020
- Fixed failing instrumentation due to the `Class not found` exception JetBrains/intellij-platform-gradle-plugin#1029
- Fixed `'compilerClassPathFromMaven' doesn't have a configured value` when resolving `java-compiler-ant-tasks` JetBrains/intellij-platform-gradle-plugin#1003
- Fixed `NoClassDefFoundError` caused by the stale `classpath.index` created by the `PathClassLoader` JetBrains/intellij-platform-gradle-plugin#1032
- Fixed the issue with a not updated GUI form during the incremental build JetBrains/intellij-platform-gradle-plugin#1044

## [1.6.0] - 2022-05-23

### Added

- Added `BuildFeature` feature flags. See [Feature Flags](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#build-features).
- Added `--jbr-illegal-access` and `-XX:+IgnoreUnrecognizedVMOptions` flags for tasks based on `RunIdeBase` to support `2022.2` which runs on Java 17
- For JBR 17, `dcevm` is bundled by default. As a consequence, separated `dcevm` and `nomod` variants are no longer available.
- `instrumentCode` task – incremental instrumentation JetBrains/intellij-platform-gradle-plugin#459
- Add `intellijRepository` to the beginning of the repository list when resolving dependencies JetBrains/intellij-platform-gradle-plugin#615
- Set `-Djdk.module.illegalAccess.silent=true` flag by default to mute `WARNING: An illegal reflective access operation has occurred`
- Add `java.system.class.loader=com.intellij.util.lang.PathClassLoader` system property for tests run on 221+
- Integration Tests: Cover `instrumentCode` task

### Changed

- Set minimal supported a Gradle version from `6.6` to `6.7`
- Throw an exception instead of warning when both `intellij.localPath` and `intellij.version` are specified
- Publish sources and Javadocs within the release JetBrains/intellij-platform-gradle-plugin#810

### Fixed

- Fix for `getHeaderField("Location") must not be null` JetBrains/intellij-platform-gradle-plugin#960
- `instrumentCode` task – changes in Kotlin code no longer rebuild the plugin JetBrains/intellij-platform-gradle-plugin#959
- Could not resolve JBR for 222-EAP-SNAPSHOT JetBrains/intellij-platform-gradle-plugin#997
- Kotlin-generated classes aren't packed into the plugin distribution JetBrains/intellij-platform-gradle-plugin#978
- Fixed version parsing for `listProductsReleases` task which resulted in incorrect IDE releases versions JetBrains/intellij-platform-gradle-plugin#995
- Release `plugin.xml` file after reading it [IDEA-291836](https://youtrack.jetbrains.com/issue/IDEA-291836)

## [1.5.3] - 2022-04-15

- Updated dependencies marked as vulnerable
- Fixed code instrumentation disabling via `tasks.instrumentCode.enabled`
- `instrumentCode` task – limit the scope of the task to `sourceSets.main.java` JetBrains/intellij-platform-gradle-plugin#459
- Introduce Gradle IntelliJ Plugin version check against the latest available

## [1.5.2] - 2022-03-31

- Add `util_rt.jar` to the classpath of run-like tasks for `2022.1+` compatibility

## [1.5.1] - 2022-03-28

- Make IDEA products releases cached hourly JetBrains/intellij-platform-gradle-plugin#848
- Fixed `ListProductReleasesTask` to return only significant versions for Android Studio JetBrains/intellij-platform-gradle-plugin#928

## [1.5.0] - 2022-03-28

- Include Android Studio builds in the `ListProductsReleasesTask` results
- Fix compiler resolution for EAP versions JetBrains/intellij-platform-gradle-plugin#906
- Initial Toolbox Enterprise integration JetBrains/intellij-platform-gradle-plugin#913
- Make IDEA products releases cached daily JetBrains/intellij-platform-gradle-plugin#848
- Fixed `ListProductsReleasesTask` to allow for empty `untilBuild` JetBrains/intellij-platform-gradle-plugin#909
- Resolved java-compiler-ant-tasks version closest lower if provided isn't available JetBrains/intellij-platform-gradle-plugin#910
- Fixed XML parsing with JAXB - drop intermediate JDOM Document

## [1.4.0] - 2022-02-11

- Fixed JBR resolving for MacOSX M1
- Fixed compiler resolution for long build numbers JetBrains/intellij-platform-gradle-plugin#883
- Build number fallback when `product-info.json` is missing JetBrains/intellij-platform-gradle-plugin#880
- Consider `sinceBuild` and `untilBuild` properties of `ListProductsReleasesTask` in task caching JetBrains/intellij-platform-gradle-plugin#891
- Introduce `jbrVariant` property next to the `jbrVersion` property in `runIde`, `runPluginVerifier`, `buildSearchableOptions`, and `runIdeForUiTest` tasks JetBrains/intellij-platform-gradle-plugin#852
- Change log level of `JbrResolver.resolveRuntime` logs from `warn` to `debug` JetBrains/intellij-platform-gradle-plugin#849
- Update runtime classpath for `221+`
- Fixed resolving Java Runtime for macOS JetBrains/intellij-platform-gradle-plugin#895
- ProductInfo: parse custom properties in `product-info.json` JetBrains/intellij-platform-gradle-plugin#897
- Make `IntelliJInstrumentCodeTask` incremental

## [1.3.1] - 2021-11-17

- Fixed execution bit filter when extracting Rider [RIDER-72922](https://youtrack.jetbrains.com/issue/RIDER-72922)
- Revert `org.jetbrains.intellij:blockmap` dependency to the latest `1.0.5` version
- Avoid querying `intellij.version` when `intellij.localPath` is set
- Fixed `BuildSearchableOptionsTask` for `2022.1+` version of IDE [RIDER-73264](https://youtrack.jetbrains.com/issue/RIDER-73264)
- `ListProductsReleasesTask`: rely on the `patchPluginXml.sinceBuild`/`patchPluginXml.untilBuild` properties instead of `intellij.version`
- `ListProductsReleasesTask`: allow using an IDE version along with build numbers

## [1.3.0] - 2021-11-15

- IntelliJ Plugin Verifier allows for running against Android Studio (i.e. `AI-2021.2.1.4`)
- Make `intellij.version` property mandatory
- Move `intellij.ideaDependency` to the `SetupDependenciesTask.idea`
- Postpone the initial dependencies downloading to the `setupDependencies` task which is run in the `afterSync` phase or by individual tasks
- Provide build information within the `META-INF/MANIFEST.MF` file
- Resolve an EAP version of the Java compiler for `LATEST-EAP-SNAPSHOT`
- Allow for using `dcevm`, `fd`, and `nomod` variants of JBR JetBrains/intellij-platform-gradle-plugin#818
- `ListProductsReleasesTask.updatesPath` changed to `ListProductsReleasesTask.updatePaths`
- `ListProductsReleasesTask.includeEAP` changed to `ListProductsReleasesTask.releaseChannels`

## [1.2.1] - 2021-10-26

- Respect `ideaDependencyCachePath` property JetBrains/intellij-platform-gradle-plugin#794
- Fix for providing dependencies after project evaluation JetBrains/intellij-platform-gradle-plugin#801
- Resolve an EAP version of the Java compiler for local EAP IDE instances JetBrains/intellij-platform-gradle-plugin#811
- Allow for passing an empty array for `runPluginVerifier.ideVersions` property JetBrains/intellij-platform-gradle-plugin#809

## [1.2.0] - 2021-09-30

- Fixed running tests on the 2021.3 platform version
- Avoid downloading IDE dependency in the configuration phase
- Deprecate `IntelliJPluginExtension.getIdeaDependency(project: Project)`
- Increase the default `runPluginVerifier.failureLevel` to `COMPATIBILITY_PROBLEMS`
- Introduce `listProductsReleases` task for listing the IDE releases matching given criteria
- Fixed resolving compiler classpath for the `instrumentCode` task when using `LATEST-EAP-SNAPSHOT` JetBrains/intellij-platform-gradle-plugin#752
- Fixed resolving `idea.platform.prefix` JetBrains/intellij-platform-gradle-plugin#772
- Fix for custom `buildDir` not used in some `instrumentCode` and `buildSearchableOptions` tasks JetBrains/intellij-platform-gradle-plugin#793

## [1.1.6] - 2021-09-05

- Fixedly returned a list of paths to IDEs downloaded for Plugin Verifier JetBrains/intellij-platform-gradle-plugin#779

## [1.1.5] - 2021-09-03

- Use target Kotlin API Version 1.3 JetBrains/intellij-platform-gradle-plugin#750
- Migrate `SignPluginTask` to use the Marketplace ZIP Signer CLI
- Fixed resolving of built-in JetBrains Runtime (JBR) JetBrains/intellij-platform-gradle-plugin#756

## [1.1.4] - 2021-07-21

- Configuration cache enhancements
- Fix `prepareTestingSandbox` not running when a test task is executed JetBrains/intellij-platform-gradle-plugin#745
- Move signPlugin file name creation to lazy JetBrains/intellij-platform-gradle-plugin#742 by @brian-mcnamara
- Better platform prefix resolving

## [1.1.3] - 2021-07-14

- Fixed dependency on `JavaScript` plugin JetBrains/intellij-platform-gradle-plugin#674
- Fixed `releaseType` resolving for Rider versions in `-EAP#-SNAPSHOT` format.
- `runPluginVerifier`: verify required Java 11 environment for Plugin Verifier `1.260+`
- `pluginVerifier` – remove support for old versions `< 1.255` hosted on Bintray
- Fixed tests configuration – 'Config Directory' does not exist exception

## [1.1.2] - 2021-07-01

- Use Gradle `ArchiveOperations` in `extractArchive` utils method JetBrains/intellij-platform-gradle-plugin#681
- Set minimal supported Gradle version to 6.6
- Use JDOM for altering `updates.xml` in `PrepareSandboxTask` to keep existing content
- Fixed incorrect output path of `JarSearchableOptionsTask` causing also duplicate entry exception JetBrains/intellij-platform-gradle-plugin#678
- Fixed incorrect plugin download URL for custom repositories JetBrains/intellij-platform-gradle-plugin#688
- Make `DownloadRobotServerPluginTask` pointing to the latest Robot Server Plugin available
- Support Maven closure in `PluginsRepositories` block
- `BuildSearchableOptionsTask` fails on macOS when resolving `javaHome` #JetBrains/intellij-platform-gradle-plugin#696
- `PrepareSandboxTask` doesn't depend on `JavaPlugin` dependencies #JetBrains/intellij-platform-gradle-plugin#451
- Remove `IntelliJPluginExtension.pluginsRepositories(block: Closure<Any>)` due to `ConfigureUtil` deprecation and a lack of typed parameters
- Remove usage of deprecated methods and classes introduced in Gradle 7.1 #JetBrains/intellij-platform-gradle-plugin#700

## [1.0.0] - 2021-05-27

- Breaking changes guide: https://lp.jetbrains.com/gradle-intellij-plugin
- Plugin Signing integration
- Lazy Configuration support
- Configuration Cache support
- Task Configuration Avoidance support
- Better CI (GitHub Actions, Qodana, Dependabot)
- Rewritten in Kotlin
- Property names cleanup (`*Repo` to `*Repository`, `*Directory` to `*Dir` – for the sake of consistency with Gradle)
- Stepping away from Bintray and JCenter

## [0.7.3] - 2021-04-26

- Migrate from bintray JetBrains/intellij-platform-gradle-plugin#594
- Exclude kotlin-reflect and kotlin-text from the runtime if kotlin is used in plugin JetBrains/intellij-platform-gradle-plugin#585
- Respect overridden `build` directory JetBrains/intellij-platform-gradle-plugin#602
- Store a cache of plugins from different custom repositories in different directories JetBrains/intellij-platform-gradle-plugin#579
- Rename dependency jars with the same name JetBrains/intellij-platform-gradle-plugin#497

## [0.7.2] - 2021-02-23

- Fix classpath for IDE without `ant` inside distribution
- Fix resolving the OS architecture

## [0.7.1] - 2021-02-22

- Fix classpath for IDE 2020.2 JetBrains/intellij-platform-gradle-plugin#601

## [0.7.0] - 2021-02-21

- Support GoLand as an SDK
- Fix javac2 dependency for project with implicit IntelliJ version JetBrains/intellij-platform-gradle-plugin#592
- Fix using query parameters in custom repository urls JetBrains/intellij-platform-gradle-plugin#589
- Support downloading JBR for `aarch64` JetBrains/intellij-platform-gradle-plugin#600
- Added ant dependencies to the testing classpath
- Fix JBR resolving after removing JavaFX from JBR in IDEA 2021.1 JetBrains/intellij-platform-gradle-plugin#599

## [0.6.5] - 2020-11-25

- Fixed not found classes from plugin dependencies in tests JetBrains/intellij-platform-gradle-plugin#570

## [0.6.4] - 2020-11-19

- `runPluginVerifier`: integrate Plugin Verifier offline mode with Gradle `offline` start parameter
- `runPluginVerifier`: introduce `verifierPath` property
- Support for Rider for Unreal Engine as an SDK

## [0.6.3] - 2020-11-09

- Fixed loading dependencies of builtin plugin JetBrains/intellij-platform-gradle-plugin#542
- Fixed loading file templates from plugins JetBrains/intellij-platform-gradle-plugin#554
- Yet another fix for class-loading in tests for IntelliJ Platform 203 and higher JetBrains/intellij-platform-gradle-plugin#561

## [0.6.2] - 2020-11-05

- `runPluginVerifier`: make ideVersions property mandatory
- `runPluginVerifier`: better handling of the exception produced by DownloadAction JetBrains/intellij-platform-gradle-plugin#553
- `runPluginVerifier`: provide URL for verifying the available IDE versions JetBrains/intellij-platform-gradle-plugin#553
- `runPluginVerifier`: fix java.nio.file.FileAlreadyExistsException as ERROR in logs JetBrains/intellij-platform-gradle-plugin#552
- Add `prepareTestingSandbox` as an input to tests

## [0.6.1] - 2020-10-29

- `runPluginVerifier`: allow specifying `ideVersions` as comma-separated String
- `runPluginVerifier`: specifying EAP build number leads to IllegalArgumentException
- `runPluginVerifier`: fix for `ArrayIndexOutOfBoundsException` when destructuring `ideVersion.split`

## [0.6.0] - 2020-10-29

- Introduced runPluginVerifier task that runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IntelliJ IDEA builds.

## [0.5.1] - 2020-10-27

- Fix class-loading in tests for IntelliJ Platform >= 203

## [0.5.0] - 2020-10-05

- Do not download dependencies during configuration phase JetBrains/intellij-platform-gradle-plugin#123
- Support multiple plugin repositories
- Support enterprise plugin repositories JetBrains/intellij-platform-gradle-plugin#15

## [0.4.26] - 2020-09-18

- Fix plugin-repository-rest-client dependency

## [0.4.25] - 2020-09-17

- Fix plugin-repository-rest-client dependency

## [0.4.24] - 2020-09-17

- Fix plugin-repository-rest-client dependency

## [0.4.23] - 2020-09-17

- Fix the compatibility issue with Kotlin 1.4 serialization JetBrains/intellij-platform-gradle-plugin#532

## [0.4.22] - 2020-09-03

- Add an option to disable auto-reload of dynamic plugins
- Documentation improvements

## [0.4.21] - 2020-05-12

- Fix adding searchable options to the distribution for Gradle > 5.1 JetBrains/intellij-platform-gradle-plugin#487

## [0.4.20] - 2020-05-06

- Fixed caching builtin plugins data
- Add annotations-19.0.0 to compile classpath by default
- Fix setting plugin name for Gradle 5.1–5.3 JetBrains/intellij-platform-gradle-plugin#481

## [0.4.19] - 2020-05-02

- Use builtin JBR from alternativeIdePath IDE JetBrains/intellij-platform-gradle-plugin#358
- Enable dependencies for builtin plugins automatically JetBrains/intellij-platform-gradle-plugin#474
- Allow referring builtin plugins by their ids rather than directory name [IDEA-233841](https://youtrack.jetbrains.com/issue/IDEA-233841)
- Require 4.9 Gradle version, dropped deprecated stuff
- Do not add junit.jar into the classpath, it may clash with junit-4.jar on certain JDKs

## [0.4.18] - 2020-04-01

- Introduced `runIdeForUiTests` task JetBrains/intellij-platform-gradle-plugin#466
- Fix unpacking JBR with JCEF on Mac JetBrains/intellij-platform-gradle-plugin#468
- Publish plugin security update JetBrains/intellij-platform-gradle-plugin#472

## [0.4.17] - 2020-03-23

- Fix platform prefix for DataGrip JetBrains/intellij-platform-gradle-plugin#458
- Enable plugin auto-reloading by default
- Upgrade plugins repository client
- Use new methods for Gradle 5.1 and higher JetBrains/intellij-platform-gradle-plugin#464
- Support JBR with JCEF JetBrains/intellij-platform-gradle-plugin#465

## [0.4.16] - 2020-01-27

- Fix downloading JBR if the temp directory and Gradle cache are on the different partitions JetBrains/intellij-platform-gradle-plugin#457
- Build a searchable options task is marked as cacheable

## [0.4.15] - 2019-12-07

- Fix uploading on Java 11 JetBrains/intellij-platform-gradle-plugin#448
- Fix instrumentation when localPath is set JetBrains/intellij-platform-gradle-plugin#443

## [0.4.14] - 2019-11-25

- Support for Gradle 6.0
- Deprecated `runIde.ideaDirectory`. `runIde.ideDirectory` should be used instead

## [0.4.13] - 2019-11-13

- Removed `intellij.useProductionClassLoaderInTests` option as we found another way to fix loading plugins in tests in 2019.3

## [0.4.12] - 2019-11-08

- More structured logging
- Introduced `intellij.useProductionClassLoaderInTests` option to control how plugin is going to be loaded in tests

## [0.4.11] - 2019-10-30

- Fix setting archive name for Gradle 5.1 and higher JetBrains/intellij-platform-gradle-plugin#436
- Fix forms compilation for Rider and Python snapshot builds. Works for Rider-2019.3-SNAPSHOT and higher JetBrains/intellij-platform-gradle-plugin#403

## [0.4.10] - 2019-08-08

- Upgrade download plugin JetBrains/intellij-platform-gradle-plugin#418
- Simplify custom runIde task configuration JetBrains/intellij-platform-gradle-plugin#401

## [0.4.9] - 2019-06-05

- Graceful handling of 404 errors when publishing a new plugin JetBrains/intellij-platform-gradle-plugin#389
- Support PyCharm as an SDK
- Fail if the plugin depends on Java plugin but doesn't declare it as a dependency

## [0.4.8] - 2019-04-16

- Gradle 5.4 compatibility
- Support for new JBR distributions layout
- Made buildSearchableOption task incremental

## [0.4.7] - 2019-03-25

- Add one more executable file in Rider SDK

## [0.4.6] - 2019-03-25

- Support Gradle 5.3 JetBrains/intellij-platform-gradle-plugin#379
- Fixed downloading JBR 8 for IDEA 2018.3 and earlier

## [0.4.5] - 2019-03-13

- Support JBR 11 from the new JetBrains Runtime Repository
- Support running using JBR 11 [IDEA-208692](https://youtrack.jetbrains.com/issue/IDEA-208692)

## [0.4.4] - 2019-02-28

- Support the new bintray repository for JetBrains Runtime artifacts
- Fixed downloading of old JBR builds JetBrains/intellij-platform-gradle-plugin#367
- Fix instrumentation for local IDE instances JetBrains/intellij-platform-gradle-plugin#369

## [0.4.3] - 2019-02-19

- Fixed downloading instrumentation dependencies for release versions
- Fixed downloading renamed JetBrains Runtime artifacts

## [0.4.2]

- Fixed removing `config/` and `system/` on running `runIde` task JetBrains/intellij-platform-gradle-plugin#359

## [0.4.1]

- Fixed plugin's sources attaching

## [0.4.0]

- Drop Gradle 2 support
- Support for CLion as a building dependency JetBrains/intellij-platform-gradle-plugin#342
- Support token-based authentication while publishing plugins JetBrains/intellij-platform-gradle-plugin#317
- Add notification about patching particular tag values and attributes in plugin.xml JetBrains/intellij-platform-gradle-plugin#284
- Fix attaching sources to bundled plugins JetBrains/intellij-platform-gradle-plugin#337
- Fix a verification message in case of default value of `description`-tag

## [0.3.12]

- Fixed resolving plugins from a custom channel JetBrains/intellij-platform-gradle-plugin#320
- Fixed building with Java 9

## [0.3.11]

- ~~fixed resolving plugins from a custom channel~~
- Fixed uploading plugins JetBrains/intellij-platform-gradle-plugin#321
- Fixed caching strategy for IDEA dependency JetBrains/intellij-platform-gradle-plugin#318

## [0.3.10]

- Fixed dependency on local plugin files
- Cache-redirector is used for downloading plugin dependencies JetBrains/intellij-platform-gradle-plugin#301

## [0.3.7]

- Fixed missing `tools.jar` on Mac JetBrains/intellij-platform-gradle-plugin#312

## [0.3.6]

- `runIde` task uses `tools.jar` from a JBR Java JetBrains/intellij-platform-gradle-plugin#307

## [0.3.5]

- Allow to override all system properties in RunIde task JetBrains/intellij-platform-gradle-plugin#304
- Move to the new url to JBR and Gradle distributions JetBrains/intellij-platform-gradle-plugin#301
- Fixed an encoding while writing plugin.xml JetBrains/intellij-platform-gradle-plugin#295

## [0.3.4]

- Gradle 4.8 compatibility JetBrains/intellij-platform-gradle-plugin#283

## [0.3.3]

- Fixed compiling JGoodies forms for IDEA version >= 182.* JetBrains/intellij-platform-gradle-plugin#290

## [0.3.2]

- Use tools.jar from java of `runIde` task [IDEA-192418](https://youtrack.jetbrains.com/issue/IDEA-192418)

## [0.3.1]

- Fix running for IDEA version < 2017.3 JetBrains/intellij-platform-gradle-plugin#273

## [0.3.0]

- Added plugin verification task: `verifyPlugin`
- Default values of `runIde` task are propagated to all RunIdeaTask-like tasks
- Enhanced plugins resolution: better error messages for unresolved dependencies and fixes JetBrains/intellij-platform-gradle-plugin#247
- Check the build number to decide whether the unzipped distribution can be reused (fixes JetBrains/intellij-platform-gradle-plugin#234)
- Download JetBrains Java runtime and use it while running IDE (fixes JetBrains/intellij-platform-gradle-plugin#192)
- Do not include plugin's jars recursively (fixes JetBrains/intellij-platform-gradle-plugin#231)
- Allow adding custom Javac2.jar to `instrumentCode` task

## [0.2.20]

- Recognize new kotlin stdlib files as part of IDEA dependency

## [0.2.19]

- Setup project plugin dependency for an already evaluated project (fixes JetBrains/intellij-platform-gradle-plugin#238)

## [0.2.18]

- Update default repository url
- Support for running GoLand

## [0.2.17]

- Fix compatibility with Gradle 4.0 new versions of Kotlin and Scala plugins (fixes JetBrains/intellij-platform-gradle-plugin#221 and JetBrains/intellij-platform-gradle-plugin#222)

## [0.2.16]

- Automatically set system properties for debugging Resharper

## [0.2.15]

- Restore scripts execution permissions in Rider distribution

## [0.2.14]

- Support RD prefix for Rider
- Avoid possible NPEs (fixes JetBrains/intellij-platform-gradle-plugin#208)

## [0.2.13]

- Gradle 4.0 compatibility fixes

## [0.2.12]

- Upgrade plugin-repository-rest-client

## [0.2.11]

- Upgrade plugin-repository-rest-client

## [0.2.10]

- Upgrade plugin-services libraries to fix 'Invalid plugin type' exception while downloading plugin dependencies (fixes JetBrains/intellij-platform-gradle-plugin#201)
- Prefer `compile` configuration for any plugins IDEA dependencies in tests (fixes JetBrains/intellij-platform-gradle-plugin#202)

## [0.2.9]

- Prefer `compile` configuration for bundled plugins IDEA dependencies in tests

## [0.2.8]

- Prefer `compile` configuration for IDEA dependencies in tests
- Prefer `compileOnly` configuration for plugins dependencies in tests

## [0.2.7]

- Avoid exception due to adding duplicated configurations

## [0.2.6]

- Prefer `compileOnly` configuration for IDEA dependencies

## [0.2.5]

- Set `buildDir` as a default cache for IDE dependencies in case of Rider plugin
- Fix Kotlin instrumentation

## [0.2.4]

- Fixed attaching sources for IDEA Ultimate and bundled plugins

## [0.2.3]

- Fixed compilation for multi-module layout

## [0.2.2]

- Added `runIde` task. `runIdea` is deprecated now (fixes JetBrains/intellij-platform-gradle-plugin#169)
- Fixed kotlin forms instrumentation (fixes JetBrains/intellij-platform-gradle-plugin#171)
- Fixed filtering out all resources of dependent plugins (fixes JetBrains/intellij-platform-gradle-plugin#172)
- Fixed intellij.systemProperties extension (fixes JetBrains/intellij-platform-gradle-plugin#173)

## [0.2.1]

- Added Rider support (fixes JetBrains/intellij-platform-gradle-plugin#167)
- Fix unresolved builtin plugins on case-insensitive file systems

## [0.2.0]

- The result artifact format is changed: now it's always a ZIP archive even if the plugin has no extra dependencies. *Note that this may change classloading (see JetBrains/intellij-platform-gradle-plugin#170)*
- Added an ability to use local IDE installation for compiling
- Result zip archive is added to `archives` configuration, built-in `assemble` task now builds the plugin distribution
- Added JPS-type for intellij dependency (fixes JetBrains/intellij-platform-gradle-plugin#106)
- PatchXml action is reimplemented, now it's possible to freely customize input files, destination directory, since/until builds, plugin description, and version
- PublishTask is reimplemented, now it's possible to set several channels to upload (fixes JetBrains/intellij-platform-gradle-plugin#117)
- 
    - it's possible to reuse reimplemented tasks in client's code
    - it's allowed to run tasks without plugin.xml
    - tasks are configured before project evaluation, `project.afterEvaluate` is not require anymore
- Fix incremental compiling after instrumenting code (fixes JetBrains/intellij-platform-gradle-plugin#116)
- Added `intellij.ideaDependencyCachePath` option (fixes JetBrains/intellij-platform-gradle-plugin#127)
- `project()` reference can be used as a plugin-dependency (fixes JetBrains/intellij-platform-gradle-plugin#17)
- Fix attaching sources of builtin plugins (fixes JetBrains/intellij-platform-gradle-plugin#153)

## [0.1.10]

- Do not override plugins directory content (temporary fix of JetBrains/intellij-platform-gradle-plugin#17)

## [0.1.9]

- Added default configuration to ivy-repositories (fixes JetBrains/intellij-platform-gradle-plugin#114)

## [0.1.6]

- External plugin directories are placed in the compiler classpath, so IDEA code insight is better for them now (fixes JetBrains/intellij-platform-gradle-plugin#105)

## [0.1.4]

- Fix incremental compilation on changing `intellij.version` (fixes JetBrains/intellij-platform-gradle-plugin#67)

## [0.1.0]

- Support external plugin dependencies

## [0.0.41]

- Fix Kotlin forms instrumentation (JetBrains/intellij-platform-gradle-plugin#73)

## [0.0.39]

- Allow making single-build plugin distributions (fixes JetBrains/intellij-platform-gradle-plugin#64)

## [0.0.37]

- Exclude kotlin dependencies if needed (fixes JetBrains/intellij-platform-gradle-plugin#57)

## [0.0.35]

- Disable automatic updates check in debug IDEA (fixes JetBrains/intellij-platform-gradle-plugin#46)

## [0.0.34]

- Support local IDE installation as a target application of `runIdea` task

## [0.0.33]

- Attach community sources to ultimate IntelliJ artifact (fixes JetBrains/intellij-platform-gradle-plugin#37)
- New extension for passing system properties to `runIdea` task (fixes JetBrains/intellij-platform-gradle-plugin#18)

## [0.0.32]

- Support compilation in IDEA 13.1 (fixes JetBrains/intellij-platform-gradle-plugin#28)

## [0.0.30]

- Fixed broken `runIdea` task

## [0.0.29]

- `cleanTest` task clean `system-test` and `config-test` directories (fixes JetBrains/intellij-platform-gradle-plugin#13)
- Do not override plugins which were installed in debug IDEA (fixes JetBrains/intellij-platform-gradle-plugin#24)

## [0.0.28]

- `RunIdeaTask` is extensible (fixes JetBrains/intellij-platform-gradle-plugin#23)
- Fix xml parsing exception (fixes JetBrains/intellij-platform-gradle-plugin#25)

## [0.0.27]

- Disabled custom class loader in tests (fixes JetBrains/intellij-platform-gradle-plugin#21)

## [0.0.25]

- Do not patch version tag if `project.version` property is not specified (fixes JetBrains/intellij-platform-gradle-plugin#11)

## [0.0.21]

- IntelliJ-specific jars are attached as compiler dependency (fixes JetBrains/intellij-platform-gradle-plugin#5)

## [0.0.10]

- Support for attaching IntelliJ sources in IDEA

[next]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.5.0...HEAD
[2.5.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.4.0...v2.5.0
[2.4.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.3.0...v2.4.0
[2.3.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.2.1...v2.3.0
[2.2.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.2.0...v2.2.1
[2.2.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.1.0...v2.2.0
[2.1.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.1...v2.1.0
[2.0.1]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0-rc2...v2.0.0
[2.0.0-rc2]: https://github.com/JetBrains/intellij-platform-gradle-plugin/compare/v2.0.0-rc1...v2.0.0-rc2
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
