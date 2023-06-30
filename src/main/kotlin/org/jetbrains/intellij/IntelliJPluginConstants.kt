// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

object IntelliJPluginConstants {
    const val PLUGIN_NAME = "Gradle IntelliJ Plugin"
    const val PLUGIN_ID = "org.jetbrains.intellij"
    const val PLUGIN_GROUP_NAME = "intellij"
    const val EXTENSION_NAME = "intellij"
    const val DEFAULT_SANDBOX = "idea-sandbox"

    const val BUILD_PLUGIN_TASK_NAME = "buildPlugin"
    const val BUILD_SEARCHABLE_OPTIONS_TASK_NAME = "buildSearchableOptions"
    const val CLASSPATH_INDEX_CLEANUP_TASK_NAME = "classpathIndexCleanup"
    const val DOWNLOAD_ANDROID_STUDIO_PRODUCT_RELEASES_XML_TASK_NAME = "downloadAndroidStudioProductReleasesXml"
    const val DOWNLOAD_IDE_PRODUCT_RELEASES_XML_TASK_NAME = "downloadIdeaProductReleasesXml"
    const val DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME = "downloadRobotServerPlugin"
    const val DOWNLOAD_ZIP_SIGNER_TASK_NAME = "downloadZipSigner"
    const val INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME = "initializeIntelliJPlugin"
    const val INSTRUMENT_CODE_TASK_NAME = "instrumentCode"
    const val INSTRUMENT_TEST_CODE_TASK_NAME = "instrumentTestCode"
    const val INSTRUMENTED_JAR_TASK_NAME = "instrumentedJar"
    const val JAR_SEARCHABLE_OPTIONS_TASK_NAME = "jarSearchableOptions"
    const val LIST_BUNDLED_PLUGINS_TASK_NAME = "listBundledPlugins"
    const val LIST_PRODUCTS_RELEASES_TASK_NAME = "listProductsReleases"
    const val PATCH_PLUGIN_XML_TASK_NAME = "patchPluginXml"
    const val PREPARE_SANDBOX_TASK_NAME = "prepareSandbox"
    const val PREPARE_TESTING_SANDBOX_TASK_NAME = "prepareTestingSandbox"
    const val PREPARE_UI_TESTING_SANDBOX_TASK_NAME = "prepareUiTestingSandbox"
    const val PRINT_BUNDLED_PLUGINS_TASK_NAME = "printBundledPlugins"
    const val PRINT_PRODUCTS_RELEASES_TASK_NAME = "printProductsReleases"
    const val PUBLISH_PLUGIN_TASK_NAME = "publishPlugin"
    const val RUN_IDE_TASK_NAME = "runIde"
    const val RUN_IDE_FOR_UI_TESTS_TASK_NAME = "runIdeForUiTests"
    const val RUN_IDE_PERFORMANCE_TEST_TASK_NAME = "runIdePerformanceTest"
    const val RUN_PLUGIN_VERIFIER_TASK_NAME = "runPluginVerifier"
    const val SETUP_DEPENDENCIES_TASK_NAME = "setupDependencies"
    const val SIGN_PLUGIN_TASK_NAME = "signPlugin"
    const val VERIFY_PLUGIN_TASK_NAME = "verifyPlugin"
    const val VERIFY_PLUGIN_CONFIGURATION_TASK_NAME = "verifyPluginConfiguration"
    const val VERIFY_PLUGIN_SIGNATURE_TASK_NAME = "verifyPluginSignature"

    val TASKS = listOf(
        BUILD_PLUGIN_TASK_NAME,
        BUILD_SEARCHABLE_OPTIONS_TASK_NAME,
        CLASSPATH_INDEX_CLEANUP_TASK_NAME,
        DOWNLOAD_ANDROID_STUDIO_PRODUCT_RELEASES_XML_TASK_NAME,
        DOWNLOAD_IDE_PRODUCT_RELEASES_XML_TASK_NAME,
        DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME,
        DOWNLOAD_ZIP_SIGNER_TASK_NAME,
        INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME,
        INSTRUMENT_CODE_TASK_NAME,
        INSTRUMENTED_JAR_TASK_NAME,
        INSTRUMENT_TEST_CODE_TASK_NAME,
        JAR_SEARCHABLE_OPTIONS_TASK_NAME,
        LIST_BUNDLED_PLUGINS_TASK_NAME,
        LIST_PRODUCTS_RELEASES_TASK_NAME,
        PATCH_PLUGIN_XML_TASK_NAME,
        PREPARE_SANDBOX_TASK_NAME,
        PREPARE_TESTING_SANDBOX_TASK_NAME,
        PREPARE_UI_TESTING_SANDBOX_TASK_NAME,
        PRINT_BUNDLED_PLUGINS_TASK_NAME,
        PRINT_PRODUCTS_RELEASES_TASK_NAME,
        PUBLISH_PLUGIN_TASK_NAME,
        RUN_IDE_TASK_NAME,
        RUN_IDE_FOR_UI_TESTS_TASK_NAME,
        RUN_IDE_PERFORMANCE_TEST_TASK_NAME,
        RUN_PLUGIN_VERIFIER_TASK_NAME,
        SETUP_DEPENDENCIES_TASK_NAME,
        SIGN_PLUGIN_TASK_NAME,
        VERIFY_PLUGIN_TASK_NAME,
        VERIFY_PLUGIN_CONFIGURATION_TASK_NAME,
        VERIFY_PLUGIN_SIGNATURE_TASK_NAME,
    )

    const val COMPILE_KOTLIN_TASK_NAME = "compileKotlin"
    const val KOTLIN_GRADLE_PLUGIN_ID = "org.jetbrains.kotlin.jvm"
    const val IDEA_GRADLE_PLUGIN_ID = "org.jetbrains.gradle.plugin.idea-ext"

    const val PLUGIN_XML_DIR_NAME = "patchedPluginXmlFiles"
    const val SEARCHABLE_OPTIONS_DIR_NAME = "searchableOptions"
    const val SEARCHABLE_OPTIONS_SUFFIX = ".searchableOptions.xml"

    // see https://github.com/JetBrains/gradle-intellij-plugin/issues/1060
    const val INTELLIJ_DEFAULT_DEPENDENCIES_CONFIGURATION_NAME = "z10_intellijDefaultDependencies"
    const val PERFORMANCE_TEST_CONFIGURATION_NAME = "z20_performanceTest"
    const val IDEA_PLUGINS_CONFIGURATION_NAME = "z50_ideaPlugins"
    const val IDEA_CONFIGURATION_NAME = "z90_intellij"
    const val INSTRUMENTED_JAR_CONFIGURATION_NAME = "instrumentedJar"
    const val INSTRUMENTED_JAR_PREFIX = "instrumented"

    const val ANNOTATIONS_DEPENDENCY_VERSION = "24.0.0"
    const val DEFAULT_IDEA_VERSION = "LATEST-EAP-SNAPSHOT"
    const val MINIMAL_SUPPORTED_GRADLE_VERSION = "7.6"

    const val RELEASE_SUFFIX_EAP = "-EAP-SNAPSHOT"
    const val RELEASE_SUFFIX_EAP_CANDIDATE = "-EAP-CANDIDATE-SNAPSHOT"
    const val RELEASE_SUFFIX_SNAPSHOT = "-SNAPSHOT"
    const val RELEASE_SUFFIX_CUSTOM_SNAPSHOT = "-CUSTOM-SNAPSHOT"

    const val RELEASE_TYPE_SNAPSHOTS = "snapshots"
    const val RELEASE_TYPE_NIGHTLY = "nightly"
    const val RELEASE_TYPE_RELEASES = "releases"

    const val MARKETPLACE_HOST = "https://plugins.jetbrains.com"
    const val IDEA_PRODUCTS_RELEASES_URL = "https://www.jetbrains.com/updates/updates.xml"
    const val ANDROID_STUDIO_PRODUCTS_RELEASES_URL = "https://jb.gg/android-studio-releases-list.xml"
    const val CACHE_REDIRECTOR = "https://cache-redirector.jetbrains.com"
    const val INTELLIJ_DEPENDENCIES = "$CACHE_REDIRECTOR/intellij-dependencies"
    const val DEFAULT_INTELLIJ_REPOSITORY = "$CACHE_REDIRECTOR/www.jetbrains.com/intellij-repository"
    const val DEFAULT_INTELLIJ_PLUGINS_REPOSITORY = "$CACHE_REDIRECTOR/plugins.jetbrains.com/maven"
    const val DEFAULT_JBR_REPOSITORY = "$CACHE_REDIRECTOR/intellij-jbr"
    const val PLUGIN_VERIFIER_REPOSITORY = "$CACHE_REDIRECTOR/packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-verifier"
    const val JAVA_COMPILER_ANT_TASKS_MAVEN_METADATA =
        "$DEFAULT_INTELLIJ_REPOSITORY/releases/com/jetbrains/intellij/java/java-compiler-ant-tasks/maven-metadata.xml"
    const val GITHUB_REPOSITORY = "https://github.com/jetbrains/gradle-intellij-plugin"

    const val VERSION_LATEST = "latest"
    const val PERFORMANCE_PLUGIN_ID = "com.jetbrains.performancePlugin"

    const val PLATFORM_TYPE_ANDROID_STUDIO = "AI"
    const val PLATFORM_TYPE_CLION = "CL"
    const val PLATFORM_TYPE_GATEWAY = "GW"
    const val PLATFORM_TYPE_GOLAND = "GO"
    const val PLATFORM_TYPE_INTELLIJ_COMMUNITY = "IC"
    const val PLATFORM_TYPE_INTELLIJ_ULTIMATE = "IU"
    const val PLATFORM_TYPE_PHPSTORM = "PS"
    const val PLATFORM_TYPE_PYCHARM = "PY"
    const val PLATFORM_TYPE_PYCHARM_COMMUNITY = "PC"
    const val PLATFORM_TYPE_RIDER = "RD"

    val PLATFORM_TYPES = listOf(
        PLATFORM_TYPE_ANDROID_STUDIO,
        PLATFORM_TYPE_CLION,
        PLATFORM_TYPE_GATEWAY,
        PLATFORM_TYPE_GOLAND,
        PLATFORM_TYPE_INTELLIJ_COMMUNITY,
        PLATFORM_TYPE_INTELLIJ_ULTIMATE,
        PLATFORM_TYPE_PHPSTORM,
        PLATFORM_TYPE_PYCHARM,
        PLATFORM_TYPE_PYCHARM_COMMUNITY,
        PLATFORM_TYPE_RIDER,
    )
}
