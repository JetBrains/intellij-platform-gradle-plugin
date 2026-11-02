// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.argumentProviders.IdeaHomePathArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.IntelliJPlatformArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.SandboxArgumentProvider
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.intellijPlatformIdeLayoutIndicesCachePath
import org.jetbrains.intellij.platform.gradle.models.IdeLayoutIndex
import org.jetbrains.intellij.platform.gradle.models.fullVersion
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.services.IdeLayoutIndexService
import org.jetbrains.intellij.platform.gradle.services.registerClassLoaderScopedBuildService
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware
import org.jetbrains.intellij.platform.gradle.utils.IntelliJPlatformJavaLauncher
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.platformPath
import org.jetbrains.intellij.platform.gradle.utils.requiredDependencyIds
import org.jetbrains.intellij.platform.gradle.utils.rootProjectPath
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import org.jetbrains.intellij.platform.gradle.utils.safelyCreatePlugin
import org.jetbrains.intellij.platform.gradle.utils.splitCommaSeparated
import org.jetbrains.intellij.platform.gradle.utils.withIdePluginManager
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Runs plugin tests against the currently selected IntelliJ Platform with the built plugin loaded.
 * It directly extends the [Test] Gradle task, which allows for an extensive configuration (system properties, memory management, and so).
 *
 * The [TestIdeTask] is a class used only for handling custom `testIde` tasks.
 * To register a customized test task, use [IntelliJPlatformTestingExtension.testIde].
 */
@UntrackedTask(because = "Should always run")
abstract class TestIdeTask : Test(), TestableAware, IntelliJPlatformVersionAware {

    @TaskAction
    override fun executeTests() {
        validateIntelliJPlatformVersion()

        super.executeTests()
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Runs tests using a custom IntelliJ Platform with the developed plugin installed."
    }

    companion object : Registrable {
        private val log = Logger(TestIdeTask::class.java)

        private val Test.sourceTask: TestableAware
            get() = when {
                this is TestIdeTask -> this
                else -> project.tasks.named<PrepareTestTask>(Tasks.PREPARE_TEST).get()
            }

        private val Test.instrumentedTestCode
            get() = project.tasks.named<InstrumentCodeTask>(Tasks.INSTRUMENT_TEST_CODE)
                .also { dependsOn(it) }
                .flatMap { it.outputDirectory }
                .let { project.files(it) }

        internal val configuration: Test.() -> Unit = {
            enableAssertions = true

            // VfsRootAccess permits dependency caches outside the user home via GRADLE_USER_HOME.
            environment("GRADLE_USER_HOME", project.gradle.gradleUserHomeDir.absolutePath)

            val intelliJPlatformConfiguration = sourceTask.intelliJPlatformConfiguration
            val intellijPlatformTestClasspathConfiguration = sourceTask.intellijPlatformTestClasspathConfiguration
            val intellijPlatformClasspathConfiguration = sourceTask.intellijPlatformClasspathConfiguration
            val intellijPlatformTestRuntimeClasspathConfiguration = sourceTask.intellijPlatformTestRuntimeClasspathConfiguration
            val intelliJPlatformTestRuntimeFixClasspathConfiguration = sourceTask.intelliJPlatformTestRuntimeFixClasspathConfiguration
            val coroutinesJavaAgentFile = sourceTask.coroutinesJavaAgentFile
            val runtimeArchitecture = sourceTask.runtimeArchitecture
            val runtimeDirectory = sourceTask.runtimeDirectory
            val runtimeMetadata = sourceTask.runtimeMetadata
            val sandboxConfigDirectory = sourceTask.sandboxConfigDirectory
            val sandboxPluginsDirectory = sourceTask.sandboxPluginsDirectory
            val sandboxSystemDirectory = sourceTask.sandboxSystemDirectory
            val sandboxLogDirectory = sourceTask.sandboxLogDirectory
            val pluginDirectory = sourceTask.pluginDirectory
            val platformPathProvider = project.provider { intelliJPlatformConfiguration.platformPath() }

            jvmArgumentProviders.add(
                IntelliJPlatformArgumentProvider(
                    intelliJPlatformConfiguration,
                    coroutinesJavaAgentFile,
                    runtimeArchitecture,
                    options = this,
                ),
            )

            jvmArgumentProviders.add(
                SandboxArgumentProvider(
                    sandboxConfigDirectory,
                    sandboxPluginsDirectory,
                    sandboxSystemDirectory,
                    sandboxLogDirectory,
                ),
            )

            jvmArgumentProviders.add(
                IdeaHomePathArgumentProvider(intelliJPlatformConfiguration),
            )

            systemProperty("idea.classpath.index.enabled", "false")
            systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
            systemProperty("idea.use.core.classloader.for.plugin.path", "true")
            systemProperty("idea.force.use.core.classloader", "true")
            systemProperty("intellij.console.log.level", "warning")
            systemProperty("intellij.testFramework.rethrow.logged.errors", "true")
            systemProperty("rider.tests.plugin.home.path", project.rootProjectPath.safePathString)
            // systemProperty("idea.use.core.classloader.for", pluginIds.joinToString(","))

            val sourceSets = project.extensions.getByType<SourceSetContainer>()
            val runtimeDependencies = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).runtimeClasspath

            // The below is needed to simulate the behavior of com.intellij.ide.plugins.cl.PluginClassLoader
            // which is present in the IDE when the plugin is used in "production".
            // https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html
            // https://github.com/JetBrains/intellij-community/blob/master/platform/core-impl/src/com/intellij/ide/plugins/cl/PluginClassLoader.kt
            //
            // The main difference is that the plugin classloader will first try to load any class from the libraries
            // present in the plugin and only then delegate the loading to the IDE and other plugins it depends on.
            //
            // This way the libs, which are present in the "lib" directory of the plugin's zip distribution file can
            // override IDE's dependencies.
            //
            // But when we run tests, the classpath is built by Gradle according to the defined dependencies, and there
            // is no separation into "plugin's dependencies" and the "IDE's dependencies"; it is one list.
            //
            // So to make the test environment more like the production environment, we should put the plugin's direct
            // dependencies the first on the classpath.
            val currentPluginLibsProvider = getCurrentPluginLibs(pluginDirectory)
            val otherPluginsLibsProvider = getOtherPluginLibs(sandboxPluginsDirectory, pluginDirectory)
            val bundledPluginsClasspathProvider = getBundledPluginsClasspath(
                platformPathProvider,
                // The test runtime fixes provide the core content modules, e.g., `lib/intellij.spellchecker.jar`.
                intellijPlatformTestClasspathConfiguration + intelliJPlatformTestRuntimeFixClasspathConfiguration,
                getTestedPluginDirectory(pluginDirectory),
            )

            // Build the classpath in the correct order:
            classpath = project.files(
                // 1. Instrumented test code (if available):
                instrumentedTestCode,
                // 2. Current plugin's libraries:
                currentPluginLibsProvider,
                // 3. Other plugins' libraries:
                otherPluginsLibsProvider,
                // 4. Test classpath configuration without IntelliJ Platform base classpath:
                intellijPlatformTestClasspathConfiguration - intellijPlatformClasspathConfiguration,
                // 5. IntelliJ Platform base classpath:
                intellijPlatformClasspathConfiguration,
                // 6. Original classpath without runtime dependencies:
                classpath - runtimeDependencies,
                // 7. Test runtime classpath configuration:
                intellijPlatformTestRuntimeClasspathConfiguration,
                // 8. Test runtime fixes classpath configuration (https://youtrack.jetbrains.com/issue/IJPL-180516):
                intelliJPlatformTestRuntimeFixClasspathConfiguration,
                // 9. Bundled plugins, see getBundledPluginsClasspath:
                bundledPluginsClasspathProvider,
            )

            testClassesDirs = instrumentedTestCode + testClassesDirs

            javaLauncher = runtimeDirectory.zip(runtimeMetadata) { directory, metadata ->
                IntelliJPlatformJavaLauncher(directory, metadata)
            }
        }

        /**
         * Load only the contents of the lib directory because some plugins have arbitrary files in their
         * distribution zip file, which break the JVM when added to the classpath.
         */
        private fun getCurrentPluginLibs(pluginDirectory: DirectoryProperty) = pluginDirectory.asFileTree.matching {
            include("${Sandbox.Plugin.LIB}/**/*.jar")
        }

        /**
         * Load only the contents of the lib directory because some plugins have arbitrary files in their
         * distribution zip file, which break the JVM when added to the classpath.
         */
        private fun getOtherPluginLibs(
            sandboxPluginsDirectory: DirectoryProperty,
            pluginDirectory: DirectoryProperty,
        ) =
            sandboxPluginsDirectory.zip(pluginDirectory) { sandboxPluginsDirectory, pluginDirectory ->
                val pluginName = pluginDirectory.asPath.name
                sandboxPluginsDirectory.asFileTree.matching {
                    include("*/${Sandbox.Plugin.LIB}/*.jar")
                    include("*/${Sandbox.Plugin.LIB_MODULES}/*.jar")
                    // Exclude the libs from the current plugin because we need to put before all other libs.
                    exclude("$pluginName/**")
                }
            }

        /**
         * Returns the directory of the tested plugin in the test sandbox, as produced by the [PrepareSandboxTask] preparing the sandbox
         * of this task, so the plugin descriptor is read only once the sandbox is prepared, also when the configuration cache is used.
         * Falls back to [pluginDirectory] if no such task is registered.
         */
        private fun Test.getTestedPluginDirectory(pluginDirectory: DirectoryProperty): Provider<Directory> {
            val prepareSandboxTaskName = when (this) {
                is TestIdeTask -> "${Tasks.PREPARE_SANDBOX}_$name"
                else -> Tasks.PREPARE_TEST_SANDBOX
            }

            return when (prepareSandboxTaskName) {
                in project.tasks.names -> project.tasks.named<PrepareSandboxTask>(prepareSandboxTaskName).flatMap { it.pluginDirectory }
                else -> pluginDirectory
            }
        }

        /**
         * Returns the JARs of the bundled plugins to add to the test classpath, as the IDE doesn't load bundled plugins
         * from its `plugins` directory in tests: all bundled plugins declared in `product-info.json`, see [IdeLayoutIndex.resolveTestPlugins],
         * with the JARs the IDE loads in production, see [IdeLayoutIndex.runtimeClasspath], except:
         * - the plugins listed in [GradleProperties.TestIdeBundledPluginsClasspathExcludes],
         * - the plugins with obfuscated classes clashing with classes of the IntelliJ Platform or other plugins,
         *   unless declared explicitly, see [IdeLayoutIndexService.resolveClassClashes],
         * - the plugins requiring an excluded plugin, transitively.
         *
         * The bundled plugins and modules present on [platformClasspath], i.e., declared explicitly, and the plugins required
         * by the tested plugin located in [testedPluginDirectory] or by the plugin dependencies present on [platformClasspath]
         * are never excluded, along with their dependencies.
         *
         * All bundled plugins share the core classloader, as test classes, loaded by it, can't access classes loaded by plugin classloaders.
         * See: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2250
         * See: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2261
         */
        private fun Test.getBundledPluginsClasspath(
            platformPathProvider: Provider<Path>,
            platformClasspath: FileCollection,
            testedPluginDirectory: Provider<Directory>,
        ): Provider<List<Path>> {
            val ideLayoutIndexService = project.gradle.registerClassLoaderScopedBuildService(IdeLayoutIndexService::class)
            val ideLayoutIndexCacheDirectoryProvider =
                project.providers.intellijPlatformIdeLayoutIndicesCachePath(project.rootProjectPath)
            val excludesProperty = GradleProperties.TestIdeBundledPluginsClasspathExcludes
            val excludesProvider = project.providers[excludesProperty]
                .map { it.splitCommaSeparated().toSet() }
            val excludesDefinedProvider = project.providers.gradleProperty(excludesProperty.toString())
                .map { true }
                .orElse(false)
            // The elements of a file collection built by a task are resolved when the test task is executed, also with the configuration cache,
            // unlike a value mapped from the task output, which can't be queried before the task is completed at all.
            val testedPluginLocationsProvider = project.files(testedPluginDirectory).elements

            return platformClasspath.elements.zip(testedPluginLocationsProvider) { elements, testedPluginLocations ->
                val service = ideLayoutIndexService.get()
                val platformPath = platformPathProvider.get()
                val normalizedPlatformPath = platformPath.absolute().normalize()
                val cacheDirectory = ideLayoutIndexCacheDirectoryProvider.get()
                val productInfo = platformPath.productInfo()
                val ideLayoutIndex = service.resolve(platformPath, cacheDirectory)
                val excludesDefined = excludesDefinedProvider.get()
                val excludesReason = when {
                    excludesDefined -> "listed in '$excludesProperty'"
                    else -> "excluded by default, see '$excludesProperty'"
                }
                val excludes = when {
                    excludesDefined -> excludesProvider.get()
                    else -> excludesProperty.forBuild(productInfo.buildNumber)
                }
                val requiredPluginIds = getRequiredPluginIds(
                    elements.map { it.asPath.absolute().normalize() }.filterNot { it.startsWith(normalizedPlatformPath) } +
                        testedPluginLocations.map { it.asPath },
                )

                val testPlugins = ideLayoutIndex.resolveTestPlugins(
                    bundledPluginIds = productInfo.bundledPlugins,
                    excludes = excludes.associateWith { excludesReason },
                    clashes = service.resolveClassClashes(platformPath, cacheDirectory),
                    declaredPlugins = ideLayoutIndex.findPresentPlugins(elements.relativeTo(platformPath)),
                    requiredPlugins = requiredPluginIds.mapNotNull(ideLayoutIndex::findByIdOrModuleId),
                )
                testPlugins.report(service, productInfo.fullVersion, excludesDefined)

                val layout = IdeLayoutIndex.Layout(productInfo)
                testPlugins.included
                    .flatMap { ideLayoutIndex.runtimeClasspath(it, layout) }
                    .map(platformPath::resolve)
                    .distinct()
            }
        }

        /**
         * Returns IDs of the plugins and modules required by the plugins located in [paths]:
         * non-optional `<depends>`, `<dependencies>` of the main module, and of content modules with `loading="required"`.
         * Collected JARs under `lib` or `lib/modules` are read from the plugin directory to resolve split-JAR modules.
         * Paths that aren't plugins, e.g., libraries, or don't exist, are skipped.
         */
        internal fun getRequiredPluginIds(paths: List<Path>) = withIdePluginManager { pluginManager ->
            paths.asSequence()
                .filter { it.exists() }
                .map { path ->
                    if (path.isRegularFile()) {
                        val lib = when (path.parent?.name) {
                            Sandbox.Plugin.LIB -> path.parent
                            Sandbox.Plugin.MODULES -> path.parent.parent.takeIf { it?.name == Sandbox.Plugin.LIB }
                            else -> null
                        }
                        lib?.parent ?: path
                    } else {
                        path
                    }
                }
                .distinct()
                .mapNotNull { runCatching { pluginManager.safelyCreatePlugin(it).getOrNull() }.getOrNull() }
                .flatMap { plugin ->
                    plugin.requiredDependencyIds { it.moduleDefinition.loadingRule.required }
                }
                .toSet()
        }

        /**
         * Logs the resolved bundled plugins: the exclusions with their reasons on the info level,
         * and the warnings about clashing classes and about exclusions ignored for declared or required plugins.
         * Ignored default exclusions are logged on the info level unless [excludesDefined] explicitly.
         */
        private fun IdeLayoutIndex.TestPlugins.report(service: IdeLayoutIndexService, version: String, excludesDefined: Boolean) {
            val total = included.size + excluded.size
            service.once("Test classpath includes ${included.size} of $total bundled plugins of $version", log::info)
            excluded.forEach {
                service.once("Bundled plugin '${it.entry.id}' is excluded from the test classpath: ${it.reason}", log::info)
            }

            excluded.filter { it.clashes.isNotEmpty() }.forEach { exclusion ->
                val id = exclusion.entry.id
                val dependents = excluded.filter { it.root?.key == exclusion.entry.key }.map { it.entry.id }
                service.once(
                    buildString {
                        append("Bundled plugin '$id' is excluded from the test classpath because ${exclusion.clashes.size} of its obfuscated classes ")
                        append("clash with classes of the IntelliJ Platform or other bundled plugins, e.g., '${exclusion.clashes.first()}'")
                        if (dependents.isNotEmpty()) {
                            append(", along with ${dependents.size} bundled plugins requiring it: ${dependents.joinToString()}")
                        }
                        append(". To keep it, declare it explicitly, e.g., with testBundledPlugin(\"$id\").")
                    },
                    log::warn,
                )
            }

            clashes.forEach { clash ->
                val id = clash.entry.id
                val message = when {
                    clash.declared ->
                        "Bundled plugin '$id' shadows ${clash.classes.size} obfuscated classes of the IntelliJ Platform or other bundled plugins " +
                            "on the test classpath, e.g., '${clash.classes.first()}', which may break tests."

                    else ->
                        "Bundled plugin '$id' is kept on the test classpath despite ${clash.classes.size} of its obfuscated classes clashing " +
                            "with classes of the IntelliJ Platform or other bundled plugins, e.g., '${clash.classes.first()}', " +
                            "as the tested plugin or its plugin dependencies require it, which may break tests. " +
                            "To put it before the IntelliJ Platform on the test classpath, declare it explicitly, e.g., with testBundledPlugin(\"$id\")."
                }
                service.once(message, log::warn)
            }

            kept.forEach {
                service.once(
                    "Bundled plugin '${it.entry.id}' is kept on the test classpath despite being ${it.reason}, " +
                        "as it is declared explicitly, or the tested plugin or its plugin dependencies require it.",
                ) { message ->
                    when {
                        excludesDefined -> log.warn(message)
                        else -> log.info(message)
                    }
                }
            }
        }

        /**
         * Returns the paths of the files located in [platformPath], relative to it, like [IdeLayoutIndex.Entry.classpath].
         */
        private fun Set<FileSystemLocation>.relativeTo(platformPath: Path): Set<String> {
            val normalizedPlatformPath = platformPath.absolute().normalize()
            return map { it.asPath.normalize() }
                .filter { it.startsWith(normalizedPlatformPath) }
                .map { normalizedPlatformPath.relativize(it).invariantSeparatorsPathString }
                .toSet()
        }

        override fun register(project: Project) =
            project.registerTask<TestIdeTask>(configuration = configuration)
    }
}
