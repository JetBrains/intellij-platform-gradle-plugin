// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.compaion

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.of
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.argumentProviders.IntelliJPlatformArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.SandboxArgumentProvider
import org.jetbrains.intellij.platform.gradle.providers.JavaRuntimeArchitectureValueSource
import org.jetbrains.intellij.platform.gradle.resolvers.path.JavaRuntimePathResolver
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeExecutable
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxAware
import org.jetbrains.intellij.platform.gradle.utils.asLenient
import org.jetbrains.intellij.platform.gradle.utils.asPath
import kotlin.io.path.absolutePathString

class TestCompanion {
    companion object : Registrable {

        private val Test.coroutinesJavaAgentFile
            get() = when {
                this is TestIdeTask -> coroutinesJavaAgentFile
                else -> {
                    val initializeIntelliJPlatformPluginTaskProvider =
                        project.tasks.named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
                    dependsOn(initializeIntelliJPlatformPluginTaskProvider)

                    initializeIntelliJPlatformPluginTaskProvider.flatMap { it.coroutinesJavaAgent }
                }
            }

        private val Test.instrumentedCode
            get() = project.tasks.named<InstrumentCodeTask>(Constants.INSTRUMENT_CODE)
                .also { dependsOn(it) }
                .flatMap { it.outputDirectory }
                .let { project.files(it) }

        private val Test.instrumentedTestCode
            get() = project.tasks.named<InstrumentCodeTask>(Constants.INSTRUMENT_TEST_CODE)
                .also { dependsOn(it) }
                .flatMap { it.outputDirectory }
                .let { project.files(it) }

        private val Test.intelliJPlatformConfiguration
            get() = when {
                this is TestIdeTask -> intelliJPlatformConfiguration
                else -> project.configurations[Configurations.INTELLIJ_PLATFORM].asLenient
            }

        private val Test.jetbrainsRuntimeConfiguration
            get() = project.configurations[Configurations.JETBRAINS_RUNTIME].asLenient

        private val Test.pluginXml
            get() = when {
                this is TestIdeTask -> pluginXml
                else -> {
                    val patchPluginXmlTaskProvider = project.tasks.named<PatchPluginXmlTask>(Tasks.PATCH_PLUGIN_XML)
                    dependsOn(patchPluginXmlTaskProvider)

                    patchPluginXmlTaskProvider.flatMap { it.outputFile }
                }
            }

        private val Test.runtimeArch
            get() = project.providers.of(JavaRuntimeArchitectureValueSource::class) {
                parameters {
                    executable.set(runtimeExecutable)
                }
            }

        private val Test.runtimeExecutable
            get() = when {
                this is TestIdeTask -> project.layout.file(
                    runtimeDirectory.map {
                        it.asPath.resolveJavaRuntimeExecutable().toFile()
                    }
                )

                else -> {
                    val javaRuntimePathResolver = JavaRuntimePathResolver(
                        jetbrainsRuntime = jetbrainsRuntimeConfiguration,
                        intellijPlatform = intelliJPlatformConfiguration,
                        javaToolchainSpec = project.the<JavaPluginExtension>().toolchain,
                        javaToolchainService = project.serviceOf<JavaToolchainService>(),
                    )

                    project.layout.file(project.provider {
                        javaRuntimePathResolver.resolve().resolveJavaRuntimeExecutable().toFile()
                    })
                }
            }

        private val Test.sandboxAware: SandboxAware
            get() = when {
                this is TestIdeTask -> this
                else -> project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_TEST_SANDBOX)
                    .also { dependsOn(it) }
                    .get()
            }

        private val Test.testCompileClasspathConfiguration
            get() = project.configurations[Configurations.External.TEST_COMPILE_CLASSPATH]

        override fun register(project: Project) {
            val configuration: Test.() -> Unit = {
                enableAssertions = true

                jvmArgumentProviders.add(
                    IntelliJPlatformArgumentProvider(
                        intelliJPlatformConfiguration,
                        coroutinesJavaAgentFile,
                        pluginXml,
                        runtimeArch,
                        options = this,
                    )
                )
                jvmArgumentProviders.add(
                    SandboxArgumentProvider(
                        sandboxAware.sandboxConfigDirectory,
                        sandboxAware.sandboxPluginsDirectory,
                        sandboxAware.sandboxSystemDirectory,
                        sandboxAware.sandboxLogDirectory,
                    )
                )

                systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
                systemProperty("idea.use.core.classloader.for.plugin.path", "true")
                systemProperty("idea.force.use.core.classloader", "true")
                // systemProperty("idea.use.core.classloader.for", pluginIds.joinToString(","))

                classpath = instrumentedCode + instrumentedTestCode + classpath + testCompileClasspathConfiguration
                testClassesDirs = instrumentedTestCode + testClassesDirs

                val executablePathProvider = project.provider {
                    runtimeExecutable.asPath.absolutePathString()
                }

                doFirst {
                    executable(executablePathProvider.get())
                }
            }

            project.registerTask<Test>(Tasks.External.TEST, configureWithType = false, configuration = configuration)
            project.registerTask<TestIdeTask>(configuration = configuration)
        }
    }
}
