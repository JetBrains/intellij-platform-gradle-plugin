// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.attributes.Attribute

object IntelliJPluginConstants {
    const val PLUGIN_NAME = "IntelliJ Platform Gradle Plugin"
    const val PLUGIN_ID = "org.jetbrains.intellij.platform"
    const val PLUGIN_BASE_ID = "$PLUGIN_ID.base"
    const val PLUGIN_TASKS_ID = "$PLUGIN_ID.tasks"

    const val PLUGIN_GROUP_NAME = "intellijPlatform"
    const val JETBRAINS_RUNTIME_VENDOR = "JetBrains"
    const val JETBRAINS_MARKETPLACE_MAVEN_GROUP = "com.jetbrains.plugins"
    const val JAVA_TEST_FIXTURES_PLUGIN_ID = "java-test-fixtures"
    const val KOTLIN_GRADLE_PLUGIN_ID = "org.jetbrains.kotlin.jvm"
    const val KOTLIN_STDLIB_DEFAULT_DEPENDENCY_PROPERTY_NAME = "kotlin.stdlib.default.dependency"
    const val KOTLIN_INCREMENTAL_USE_CLASSPATH_SNAPSHOT = "kotlin.incremental.useClasspathSnapshot"
    const val COMPILE_KOTLIN_TASK_NAME = "compileKotlin"
    const val TEST_TASK_NAME = "test"
    const val VERSION_LATEST = "latest"

    object Extensions {
        const val IDES = "ides"
        const val IDEA_VERSION = "ideaVersion"
        const val INTELLIJ_PLATFORM = "intellijPlatform"
        const val PLUGIN_CONFIGURATION = "pluginConfiguration"
        const val PLUGIN_VERIFIER = "pluginVerifier"
        const val PRODUCT_DESCRIPTOR = "productDescriptor"
        const val SIGNING = "signing"
        const val VENDOR = "vendor"
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
        const val INTELLIJ_PLATFORM_PRODUCT_INFO = "intellijPlatformProductInfo"
        const val INTELLIJ_PLUGIN_VERIFIER = "intellijPluginVerifier"
        const val INTELLIJ_PLUGIN_VERIFIER_IDES = "intellijPluginVerifierIdes"
        const val INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY = "intellijPluginVerifierIdesDependency"
        const val INTELLIJ_PLUGIN_VERIFIER_IDES_LOCAL_INSTANCE = "intellijPluginVerifierIdesLocalInstance"
        const val MARKETPLACE_ZIP_SIGNER = "marketplaceZipSigner"
        const val JETBRAINS_RUNTIME = "jetbrainsRuntime"
        const val JETBRAINS_RUNTIME_DEPENDENCY = "jetbrainsRuntimeDependency"
        const val JETBRAINS_RUNTIME_LOCAL_INSTANCE = "jetbrainsRuntimeLocalInstance"
        const val TEST_FIXTURES_COMPILE_ONLY = "testFixturesCompileOnly"

        object Attributes {
            val bundledPluginsList = Attribute.of("intellijPlatformBundledPluginsList", Boolean::class.javaObjectType)
            val collected = Attribute.of("intellijPlatformCollected", Boolean::class.javaObjectType)
            val extracted = Attribute.of("intellijPlatformExtracted", Boolean::class.javaObjectType)
            val binaryReleaseExtracted = Attribute.of("intellijPlatformPluginVerifierIdeExtracted", Boolean::class.javaObjectType)
            val productInfo = Attribute.of("intellijPlatformProductInfo", Boolean::class.javaObjectType)
        }
    }

    object Tasks {
        const val APPLY_RECOMMENDED_PLUGIN_VERIFIER_IDES = "applyRecommendedPluginVerifierIdes"
        const val BUILD_PLUGIN = "buildPlugin" // TODO: check
        const val BUILD_SEARCHABLE_OPTIONS = "buildSearchableOptions"
        const val DOWNLOAD_ANDROID_STUDIO_PRODUCT_RELEASES_XML = "downloadAndroidStudioProductReleasesXml"
        const val DOWNLOAD_IDEA_PRODUCT_RELEASES_XML = "downloadIdeaProductReleasesXml"
        const val INITIALIZE_INTELLIJ_PLATFORM_PLUGIN = "initializeIntellijPlatformPlugin"
        const val INSTRUMENTED_JAR = "instrumentedJar" // TODO: check
        const val JAR_SEARCHABLE_OPTIONS = "jarSearchableOptions"
        const val LIST_BUNDLED_PLUGINS = "listBundledPlugins"
        const val LIST_PRODUCTS_RELEASES = "listProductsReleases"
        const val PATCH_PLUGIN_XML = "patchPluginXml" // TODO: check
        const val PREPARE_SANDBOX = "prepareSandbox" // TODO: check
        const val PREPARE_TESTING_SANDBOX = "prepareTestingSandbox" // TODO: check
        const val PREPARE_UI_TESTING_SANDBOX = "prepareUiTestingSandbox" // TODO: check
        const val PRINT_BUNDLED_PLUGINS = "printBundledPlugins"
        const val PRINT_PRODUCTS_RELEASES = "printProductsReleases"
        const val RUN_IDE = "runIde" // TODO: check
        const val RUN_PLUGIN_VERIFIER = "runPluginVerifier" // TODO: check
        const val SETUP_DEPENDENCIES = "setupDependencies"
        const val SIGN_PLUGIN = "signPlugin"
        const val TEST_IDE = "testIde" // TODO: check
        const val TEST_UI_IDE = "testUiIde" // TODO: check
        const val VERIFY_PLUGIN = "verifyPlugin" // TODO: check
        const val VERIFY_PLUGIN_CONFIGURATION = "verifyPluginConfiguration" // TODO: check
        const val VERIFY_PLUGIN_SIGNATURE = "verifyPluginSignature"
    }

    object Sandbox {
        const val CONTAINER = "idea-sandbox"
        const val CONFIG = "config"
        const val PLUGINS = "plugins"
        const val SYSTEM = "system"
    }

    object Locations {
        const val ANDROID_STUDIO_BINARY_RELEASES = "https://redirector.gvt1.com/edgedl/android/studio/ide-zips"
        const val DOWNLOAD = "https://download.jetbrains.com"
        const val CACHE_REDIRECTOR = "https://cache-redirector.jetbrains.com"
        const val GITHUB_REPOSITORY = "https://github.com/jetbrains/gradle-intellij-plugin"
        const val INTELLIJ_DEPENDENCIES_REPOSITORY = "$CACHE_REDIRECTOR/intellij-dependencies"
        const val JETBRAINS_RUNTIME_REPOSITORY = "$CACHE_REDIRECTOR/intellij-jbr"
        const val MAVEN_REPOSITORY = "https://repo1.maven.org/maven2"
        const val PRODUCTS_RELEASES_ANDROID_STUDIO = "https://jb.gg/android-studio-releases-list.xml"
        const val PRODUCTS_RELEASES_JETBRAINS_IDES = "https://www.jetbrains.com/updates/updates.xml"
        const val PLUGIN_VERIFIER_REPOSITORY = "$CACHE_REDIRECTOR/packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-verifier"
    }

    const val CLASSPATH_INDEX_CLEANUP_TASK_NAME = "classpathIndexCleanup"
    const val DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME = "downloadRobotServerPlugin"
    const val INSTRUMENT_CODE_TASK_NAME = "instrumentCode"
    const val INSTRUMENT_TEST_CODE_TASK_NAME = "instrumentTestCode"
    const val PUBLISH_PLUGIN_TASK_NAME = "publishPlugin"

    const val RUN_IDE_FOR_UI_TESTS_TASK_NAME = "runIdeForUiTests"
    const val RUN_IDE_PERFORMANCE_TEST_TASK_NAME = "runIdePerformanceTest"

    val TASKS = listOf(
        Tasks.BUILD_PLUGIN,
//        BUILD_SEARCHABLE_OPTIONS_TASK_NAME,
//        CLASSPATH_INDEX_CLEANUP_TASK_NAME,
//        DOWNLOAD_ANDROID_STUDIO_PRODUCT_RELEASES_XML_TASK_NAME,
//        DOWNLOAD_IDE_PRODUCT_RELEASES_XML_TASK_NAME,
//        DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME,
        Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN,
//        INSTRUMENT_CODE_TASK_NAME,
        Tasks.INSTRUMENTED_JAR,
//        INSTRUMENT_TEST_CODE_TASK_NAME,
//        JAR_SEARCHABLE_OPTIONS_TASK_NAME,
        Tasks.LIST_BUNDLED_PLUGINS,
        Tasks.PATCH_PLUGIN_XML,
        Tasks.PREPARE_SANDBOX,
        Tasks.PREPARE_TESTING_SANDBOX,
        Tasks.PREPARE_UI_TESTING_SANDBOX,
//        PRINT_BUNDLED_PLUGINS_TASK_NAME,
//        PRINT_PRODUCTS_RELEASES_TASK_NAME,
//        PUBLISH_PLUGIN_TASK_NAME,
        Tasks.RUN_IDE,
//        RUN_IDE_FOR_UI_TESTS_TASK_NAME,
//        RUN_IDE_PERFORMANCE_TEST_TASK_NAME,
        Tasks.RUN_PLUGIN_VERIFIER,
        Tasks.SETUP_DEPENDENCIES,
        Tasks.SIGN_PLUGIN,
        Tasks.TEST_IDE,
        Tasks.VERIFY_PLUGIN,
        Tasks.VERIFY_PLUGIN_CONFIGURATION,
        Tasks.VERIFY_PLUGIN_SIGNATURE,
    )


    const val SEARCHABLE_OPTIONS_DIR_NAME = "searchableOptions"
    const val SEARCHABLE_OPTIONS_SUFFIX = ".searchableOptions.xml"

    // see https://github.com/JetBrains/gradle-intellij-plugin/issues/1060
    @Deprecated("Deprecated in 2.0")
    const val INTELLIJ_DEFAULT_DEPENDENCIES_CONFIGURATION_NAME = "z10_intellijDefaultDependencies"

    @Deprecated("Deprecated in 2.0")
    const val PERFORMANCE_TEST_CONFIGURATION_NAME = "z20_performanceTest"

    @Deprecated("Deprecated in 2.0")
    const val IDEA_PLUGINS_CONFIGURATION_NAME = "z50_ideaPlugins"

    @Deprecated("Deprecated in 2.0")
    const val IDEA_CONFIGURATION_NAME = "z90_intellij"

    const val INSTRUMENTED_JAR_CONFIGURATION_NAME = "instrumentedJar"
    const val INSTRUMENTED_JAR_PREFIX = "instrumented"

    const val DEFAULT_IDEA_VERSION = "LATEST-EAP-SNAPSHOT"
    const val MINIMAL_SUPPORTED_GRADLE_VERSION = "8.0"
    const val MINIMAL_SUPPORTED_INTELLIJ_PLATFORM_VERSION = "223"

    const val RELEASE_SUFFIX_EAP = "-EAP-SNAPSHOT"
    const val RELEASE_SUFFIX_EAP_CANDIDATE = "-EAP-CANDIDATE-SNAPSHOT"
    const val RELEASE_SUFFIX_SNAPSHOT = "-SNAPSHOT"
    const val RELEASE_SUFFIX_CUSTOM_SNAPSHOT = "-CUSTOM-SNAPSHOT"

    const val MARKETPLACE_HOST = "https://plugins.jetbrains.com"
    const val DEFAULT_INTELLIJ_REPOSITORY = "${Locations.CACHE_REDIRECTOR}/www.jetbrains.com/intellij-repository"
    const val DEFAULT_INTELLIJ_PLUGINS_REPOSITORY = "${Locations.CACHE_REDIRECTOR}/plugins.jetbrains.com/maven"
    const val JAVA_COMPILER_ANT_TASKS_MAVEN_METADATA =
        "$DEFAULT_INTELLIJ_REPOSITORY/releases/com/jetbrains/intellij/java/java-compiler-ant-tasks/maven-metadata.xml"

    const val PERFORMANCE_PLUGIN_ID = "com.jetbrains.performancePlugin"
}
