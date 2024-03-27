// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.HelpTasksPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.util.GradleVersion
import org.jetbrains.intellij.platform.gradle.utils.toVersion

object Constants {
    const val CACHE_DIRECTORY = ".intellijPlatform"
    const val JETBRAINS_RUNTIME_VENDOR = "JetBrains"
    const val JETBRAINS_MARKETPLACE_MAVEN_GROUP = "com.jetbrains.plugins"
    const val VERSION_CURRENT = "current"
    const val VERSION_LATEST = "latest"

    object Plugin {
        const val ID = "org.jetbrains.intellij.platform"
        const val NAME = "IntelliJ Platform Gradle Plugin"
        const val GROUP_NAME = "intellij platform"
        const val LOG_PREFIX = "[$ID]"
    }

    object Plugins {
        const val BASE = "${Plugin.ID}.base"
        const val BUILD = "${Plugin.ID}.build"
        const val MODULE = "${Plugin.ID}.module"
        const val PUBLISH = "${Plugin.ID}.publish"
        const val RUN = "${Plugin.ID}.run"
        const val SETTINGS = "${Plugin.ID}.settings"
        const val TEST = "${Plugin.ID}.test"
        const val VERIFY = "${Plugin.ID}.verify"

        object External {
            const val JAVA_TEST_FIXTURES = "java-test-fixtures"
            const val KOTLIN = "org.jetbrains.kotlin.jvm"
        }
    }

    object Constraints {
        val MINIMAL_GRADLE_VERSION: GradleVersion = GradleVersion.version("8.1")
        val MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER = "223".toVersion()
        val MINIMAL_INTELLIJ_PLATFORM_VERSION = "2022.3".toVersion()
        val MINIMAL_SPLIT_MODE_BUILD_NUMBER = "241.14473".toVersion()
    }

    object Extensions {
        const val IDES = "ides"
        const val IDEA_VERSION = "ideaVersion"
        const val INTELLIJ_PLATFORM = "intellijPlatform"
        const val PLUGIN_CONFIGURATION = "pluginConfiguration"
        const val PRODUCT_DESCRIPTOR = "productDescriptor"
        const val PUBLISHING = "publishing"
        const val SIGNING = "signing"
        const val VENDOR = "vendor"
        const val VERIFY_PLUGIN = "verifyPlugin"
    }

    object Configurations {
        const val INTELLIJ_PLATFORM_DEPENDENCY = "intellijPlatformDependency"
        const val INTELLIJ_PLATFORM_LOCAL_INSTANCE = "intellijPlatformLocalInstance"
        const val INTELLIJ_PLATFORM = "intellijPlatform"
        const val INTELLIJ_PLATFORM_PLUGINS = "intellijPlatformPlugins"
        const val INTELLIJ_PLATFORM_PLUGINS_EXTRACTED = "intellijPlatformPluginsExtracted"
        const val INTELLIJ_PLATFORM_BUNDLED_PLUGINS = "intellijPlatformBundledPlugins"
        const val INTELLIJ_PLATFORM_BUNDLED_PLUGINS_LIST = "intellijPlatformBundledPluginsList"
        const val INTELLIJ_PLATFORM_DEPENDENCIES = "intellijPlatformDependencies"
        const val INTELLIJ_PLATFORM_JAVA_COMPILER = "intellijPlatformJavaCompiler"
        const val INTELLIJ_PLATFORM_TEST_DEPENDENCIES = "intellijPlatformTestDependencies"
        const val INTELLIJ_PLUGIN_VERIFIER = "intellijPluginVerifier"
        const val INTELLIJ_PLUGIN_VERIFIER_IDES = "intellijPluginVerifierIdes"
        const val INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY = "intellijPluginVerifierIdesDependency"
        const val INTELLIJ_PLUGIN_VERIFIER_IDES_LOCAL_INSTANCE = "intellijPluginVerifierIdesLocalInstance"
        const val MARKETPLACE_ZIP_SIGNER = "marketplaceZipSigner"
        const val JETBRAINS_RUNTIME = "jetbrainsRuntime"
        const val JETBRAINS_RUNTIME_DEPENDENCY = "jetbrainsRuntimeDependency"
        const val JETBRAINS_RUNTIME_LOCAL_INSTANCE = "jetbrainsRuntimeLocalInstance"
        const val TEST_FIXTURES_COMPILE_ONLY = "testFixturesCompileOnly"
        const val TEST_FIXTURES_COMPILE_CLASSPATH = "testFixturesCompileClasspath"

        object Attributes {
            val bundledPluginsList = Attribute.of("intellijPlatformBundledPluginsList", Boolean::class.javaObjectType)
            val collected = Attribute.of("intellijPlatformCollected", Boolean::class.javaObjectType)
            val extracted = Attribute.of("intellijPlatformExtracted", Boolean::class.javaObjectType)
            val binaryReleaseExtracted = Attribute.of("intellijPlatformPluginVerifierIdeExtracted", Boolean::class.javaObjectType)
        }

        object Dependencies {
            const val LOCAL_IDE_GROUP = "localIde"
            const val BINARY_RELEASE_GROUP = "binaryRelease"
            const val BUNDLED_PLUGIN_GROUP = "bundledPlugin"
        }

        object External {
            const val COMPILE_CLASSPATH = JvmConstants.COMPILE_CLASSPATH_CONFIGURATION_NAME
            const val COMPILE_ONLY = JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME
            const val IMPLEMENTATION = JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME
            const val RUNTIME_CLASSPATH = JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
            const val RUNTIME_ELEMENTS = JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME
            const val TEST_COMPILE_CLASSPATH = JvmConstants.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME
            const val TEST_COMPILE_ONLY = JvmConstants.TEST_COMPILE_ONLY_CONFIGURATION_NAME
        }
    }

    // TODO: fix that
    const val INSTRUMENT_CODE = "instrumentCode"
    const val INSTRUMENT_TEST_CODE = "instrumentTestCode"

    object Tasks {
        const val BUILD_PLUGIN = "buildPlugin"
        const val BUILD_SEARCHABLE_OPTIONS = "buildSearchableOptions"
        const val INITIALIZE_INTELLIJ_PLATFORM_PLUGIN = "initializeIntellijPlatformPlugin"
        const val INSTRUMENTED_JAR = "instrumentedJar"
        const val JAR_SEARCHABLE_OPTIONS = "jarSearchableOptions"
        const val PATCH_PLUGIN_XML = "patchPluginXml" // TODO: check
        const val PREPARE_SANDBOX = "prepareSandbox" // TODO: check
        const val PREPARE_TEST_SANDBOX = "prepareTestingSandbox" // TODO: check
        const val PREPARE_UI_TEST_SANDBOX = "prepareUiTestingSandbox" // TODO: check
        const val PRINT_BUNDLED_PLUGINS = "printBundledPlugins"
        const val PRINT_PRODUCTS_RELEASES = "printProductsReleases"
        const val PUBLISH_PLUGIN = "publishPlugin"
        const val RUN_IDE = "runIde" // TODO: check
        const val SIGN_PLUGIN = "signPlugin"
        const val TEST_IDE_PERFORMANCE = "testIdePerformance" // TODO: check
        const val TEST_IDE_UI = "testIdeUi" // TODO: check
        const val VERIFY_PLUGIN = "verifyPlugin" // TODO: check
        const val VERIFY_PLUGIN_STRUCTURE = "verifyPluginStructure" // TODO: check
        const val VERIFY_PLUGIN_PROJECT_CONFIGURATION = "verifyPluginProjectConfiguration" // TODO: check
        const val VERIFY_PLUGIN_SIGNATURE = "verifyPluginSignature"

        object External {
            const val ASSEMBLE = LifecycleBasePlugin.ASSEMBLE_TASK_NAME
            const val CLASSES = JavaPlugin.CLASSES_TASK_NAME
            const val CLEAN = LifecycleBasePlugin.CLEAN_TASK_NAME
            const val COMPILE_JAVA = JavaPlugin.COMPILE_JAVA_TASK_NAME
            const val COMPILE_KOTLIN = "compileKotlin"
            const val DEPENDENCIES = HelpTasksPlugin.DEPENDENCIES_TASK
            const val JAR = JavaPlugin.JAR_TASK_NAME
            const val PROCESS_RESOURCES = JavaPlugin.PROCESS_RESOURCES_TASK_NAME
            const val TEST = JavaPlugin.TEST_TASK_NAME
        }
    }

    object Sandbox {
        const val CONTAINER = "idea-sandbox"
        const val CONFIG = "config"
        const val PLUGINS = "plugins"
        const val SYSTEM = "system"
        const val LOG = "log"
    }

    object Locations {
        const val ANDROID_STUDIO_BINARY_RELEASES = "https://redirector.gvt1.com/edgedl/android/studio"
        const val DOWNLOAD = "https://download.jetbrains.com"
        const val CACHE_REDIRECTOR = "https://cache-redirector.jetbrains.com"
        const val GITHUB_REPOSITORY = "https://github.com/jetbrains/intellij-platform-gradle-plugin"
        const val INTELLIJ_REPOSITORY = "$CACHE_REDIRECTOR/intellij-repository"
        const val INTELLIJ_DEPENDENCIES_REPOSITORY = "$CACHE_REDIRECTOR/intellij-dependencies"
        const val JETBRAINS_RUNTIME_REPOSITORY = "$CACHE_REDIRECTOR/intellij-jbr"
        const val MAVEN_GRADLE_PLUGIN_PORTAL_REPOSITORY = "https://plugins.gradle.org/m2"
        const val MAVEN_REPOSITORY = "https://repo1.maven.org/maven2"
        const val MARKETPLACE = "https://plugins.jetbrains.com"
        const val PRODUCTS_RELEASES_ANDROID_STUDIO = "https://jb.gg/android-studio-releases-list.xml"
        const val PRODUCTS_RELEASES_JETBRAINS_IDES = "https://www.jetbrains.com/updates/updates.xml"
    }

    object GradleProperties {
        const val INTELLIJ_PLATFORM_CACHE = "${Plugin.ID}.intellijPlatformCache"
        const val LOCAL_PLATFORM_ARTIFACTS = "${Plugin.ID}.localPlatformArtifacts"
        const val KOTLIN_STDLIB_DEFAULT_DEPENDENCY = "kotlin.stdlib.default.dependency"
    }

    const val CLASSPATH_INDEX_CLEANUP_TASK_NAME = "classpathIndexCleanup"
    const val DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME = "downloadRobotServerPlugin"
    const val INSTRUMENT_TEST_CODE_TASK_NAME = "instrumentTestCode"

    const val RUN_IDE_FOR_UI_TESTS_TASK_NAME = "runIdeForUiTests"
    const val RUN_IDE_PERFORMANCE_TEST_TASK_NAME = "runIdePerformanceTest"

    const val DEFAULT_INTELLIJ_PLUGINS_REPOSITORY = "${Locations.CACHE_REDIRECTOR}/plugins.jetbrains.com/maven"

    const val PERFORMANCE_PLUGIN_ID = "com.jetbrains.performancePlugin"
}
