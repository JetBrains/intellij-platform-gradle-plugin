// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.ide.IdeManager
import com.jetbrains.plugin.structure.intellij.plugin.module.IdeModule
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.argumentProviders.IntelliJPlatformArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.SandboxArgumentProvider
import org.jetbrains.intellij.platform.gradle.artifacts.transform.CollectorTransformer
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware
import org.jetbrains.intellij.platform.gradle.utils.IntelliJPlatformJavaLauncher
import org.jetbrains.intellij.platform.gradle.utils.asPath
import kotlin.io.path.exists
import kotlin.io.path.extension
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

            jvmArgumentProviders.add(
                IntelliJPlatformArgumentProvider(
                    sourceTask.intelliJPlatformConfiguration,
                    sourceTask.coroutinesJavaAgentFile,
                    sourceTask.runtimeArchitecture,
                    options = this,
                )
            )

            jvmArgumentProviders.add(
                SandboxArgumentProvider(
                    sourceTask.sandboxConfigDirectory,
                    sourceTask.sandboxPluginsDirectory,
                    sourceTask.sandboxSystemDirectory,
                    sourceTask.sandboxLogDirectory,
                )
            )

            systemProperty("idea.classpath.index.enabled", "false")
            systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
            systemProperty("idea.use.core.classloader.for.plugin.path", "true")
            systemProperty("idea.force.use.core.classloader", "true")
            systemProperty("intellij.testFramework.rethrow.logged.errors", "true")
            // systemProperty("idea.use.core.classloader.for", pluginIds.joinToString(","))

            val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
            val runtimeDependencies = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).runtimeClasspath

            // Provide IntelliJ Platform product modules
            // TODO: relay eventually on Plugin Verifier
            val productModules = project.files(project.provider {
                val intellijPlatformPath = sourceTask.platformPath
                sourceTask.productInfo.layout
                    .asSequence()
                    .filter { it.kind == ProductInfo.LayoutItemKind.productModuleV2 }
                    .flatMap { it.classPath }
                    .map { intellijPlatformPath.resolve(it) }
                    .mapNotNull { it.takeIf { it.exists() } }
                    .toSet()
            })

            // Provide IntelliJ Platform bundled plugins
            // TODO: relay eventually on Plugin Verifier
            val bundledPlugins = project.files(project.provider {
                val bundledPluginIds = sourceTask.productInfo.bundledPlugins
                    // com.intellij is covered by base jars already
                    // com.intellij.openRewrite fails as it has `testServiceImplementation` and we can't find test class
                    .minus(listOf("com.intellij", "com.intellij.openRewrite"))
                val ide = IdeManager.createManager().createIde(sourceTask.platformPath)

                bundledPluginIds
                    .mapNotNull { ide.findPluginById(it) }
                    .flatMap { bundledPlugin ->
                        when {
                            bundledPlugin is IdeModule -> bundledPlugin.classpath
                            bundledPlugin.originalFile == null -> emptyList()
                            else -> {
                                val file = bundledPlugin.originalFile
                                val pluginDirectory = when {
                                    file?.extension == "jar" -> file.parent.parent
                                    else -> file
                                }
                                pluginDirectory?.let { CollectorTransformer.collectJars(it) } ?: emptyList()
                            }
                        }
                    }.toSet()
            })

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
            // Removes the default runtime dependencies from the classpath because their order is unknown.
            classpath -= runtimeDependencies

            val currentPluginLibsProvider = getCurrentPluginLibs()
            val otherPluginsLibsProvider = getOtherPluginLibs()
            classpath = project.files(currentPluginLibsProvider, otherPluginsLibsProvider) + classpath

            // Add IDE modules at the end.
            classpath += productModules
            classpath += bundledPlugins

            // Since this code is getting called before the value of "project.extensionProvider.get().instrumentCode"
            // is known, we can't add "instrumentedTestCode" to the classpath only when needed.
            // Because of that, removing the original compiled test classes added by Gradle like this:
            // classpath -= sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME).output.classesDirs
            // is not possible because it will break the tests when "instrumentCode" is off.
            // Because if we remove it, there is nothing else to replace it with.
            // So we add it to the beginning of the classpath, so that when "instrumentCode" is on and both:
            // the instrumented test code and non-instrumented test code are present,
            // the instrumented version will override the other because it comes first.
            //
            // But when "instrumentCode" was on, the classes were generated, then it was turned off, and the next build
            // runs without a clean, the instrumented code will still be added to the classpath, as long as it exists.
            classpath = instrumentedTestCode + classpath

            testClassesDirs = instrumentedTestCode + testClassesDirs

            javaLauncher = sourceTask.runtimeDirectory.zip(sourceTask.runtimeMetadata) { directory, metadata ->
                IntelliJPlatformJavaLauncher(directory, metadata)
            }
        }

        /**
         * Load only the contents of the lib directory because some plugins have arbitrary files in their
         * distribution zip file, which break the JVM when added to the classpath.
         */
        private fun Test.getCurrentPluginLibs() = sourceTask.pluginDirectory.asFileTree.matching {
            include("${Sandbox.Plugin.LIB}/**/*.jar")
        }

        /**
         * Load only the contents of the lib directory because some plugins have arbitrary files in their
         * distribution zip file, which break the JVM when added to the classpath.
         */
        private fun Test.getOtherPluginLibs() =
            sourceTask.sandboxPluginsDirectory.zip(sourceTask.pluginDirectory) { sandboxPluginsDirectory, pluginDirectory ->
                val pluginName = pluginDirectory.asPath.name
                sandboxPluginsDirectory.asFileTree.matching {
                    include("*/${Sandbox.Plugin.LIB}/**/*.jar")
                    // Exclude the libs from the current plugin because we need to put before all other libs.
                    exclude("$pluginName/**")
                }
            }

        /**
         * Unfortunately, this is possible only during the execution phase, when all the directories in the build dir
         * are already created.
         *
         * To do this in advance, during the configuration phase, we need to know names of the directories for other
         * plugins in the sandbox.
         * Which can be done only if they're named using plugin id.
         * But even then it may not work if the plugin defines an alias for its ID.
         *
         * Returns a list of directories in the sandbox plugins dir, omitting the current plugin,
         * ordered according to the order of the current plugin dependencies, as returned by the IdePluginManager.
         *
         * In the "production" (like when running plugin in the IDE instead of tests), if the current plugin class loader doesn't resolve a class,
         * its loading is delegated to the class loaders of other plugins it depends on.
         *
         * And there the order might depend on the order of the dependencies in the "plugin.xml".
         * So here we rely on the order provided by the IdePluginManager.
         */
        /*
        private fun Test.getOrderedOtherPluginsDirs(): Provider<MutableList<Path>> {
            val currentPluginName = project.extensionProvider.flatMap { it.projectName }
            val currentPluginDirectory = sourceTask.sandboxPluginsDirectory.dir(currentPluginName)
            val pluginManager = IdePluginManager.createManager()

            val currentPluginDependencyIds = project.providers.provider {
                pluginManager.safelyCreatePlugin(currentPluginDirectory.get().asPath, false)
                    .getOrThrow()
                    .dependencies
                    .map { it.id }
            }

             return sourceTask.sandboxPluginsDirectory.map {
                Files.list(it.asPath) // <<< Not possible during the configuration phase.
                    // Filter out the current plugin.
                    .filter { it.name != currentPluginName.get() }
                    .map { Pair(pluginManager.safelyCreatePlugin(it, false).getOrNull()?.pluginId, it) }
                    .map { (pluginId, pluginDirPath) ->
                        Triple(pluginId, pluginDirPath, currentPluginDependencyIds.get().indexOf(pluginId))
                    }
                    .sorted(Comparator.comparingInt { (_, _, orderInTheDependencies) -> orderInTheDependencies })
                    .map { (_, pluginDirPath, _) -> pluginDirPath }
                    .toList()
            }
        }
        */

        override fun register(project: Project) =
            project.registerTask<TestIdeTask>(configuration = configuration)
    }
}
