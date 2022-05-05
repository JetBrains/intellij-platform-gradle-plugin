// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

object IntelliJPluginConstants {
    const val NAME = "Gradle IntelliJ Plugin"
    const val ID = "org.jetbrains.intellij"
    const val GROUP_NAME = "intellij"
    const val EXTENSION_NAME = "intellij"
    const val DEFAULT_SANDBOX = "idea-sandbox"
    const val PATCH_PLUGIN_XML_TASK_NAME = "patchPluginXml"
    const val PLUGIN_XML_DIR_NAME = "patchedPluginXmlFiles"
    const val PREPARE_SANDBOX_TASK_NAME = "prepareSandbox"
    const val PREPARE_TESTING_SANDBOX_TASK_NAME = "prepareTestingSandbox"
    const val PREPARE_UI_TESTING_SANDBOX_TASK_NAME = "prepareUiTestingSandbox"
    const val DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME = "downloadRobotServerPlugin"
    const val RUN_PLUGIN_VERIFIER_TASK_NAME = "runPluginVerifier"
    const val VERIFY_PLUGIN_TASK_NAME = "verifyPlugin"
    const val RUN_IDE_TASK_NAME = "runIde"
    const val RUN_IDE_PERFORMANCE_TEST_TASK_NAME = "runIdePerformanceTest"
    const val RUN_IDE_FOR_UI_TESTS_TASK_NAME = "runIdeForUiTests"
    const val BUILD_SEARCHABLE_OPTIONS_TASK_NAME = "buildSearchableOptions"
    const val SEARCHABLE_OPTIONS_DIR_NAME = "searchableOptions"
    const val JAR_SEARCHABLE_OPTIONS_TASK_NAME = "jarSearchableOptions"
    const val BUILD_PLUGIN_TASK_NAME = "buildPlugin"
    const val SIGN_PLUGIN_TASK_NAME = "signPlugin"
    const val PUBLISH_PLUGIN_TASK_NAME = "publishPlugin"
    const val LIST_PRODUCTS_RELEASES_TASK_NAME = "listProductsReleases"
    const val SETUP_DEPENDENCIES_TASK_NAME = "setupDependencies"

    const val IDEA_CONFIGURATION_NAME = "idea"
    const val PERFORMANCE_TEST_CONFIGURATION_NAME = "performanceTest"
    const val IDEA_PLUGINS_CONFIGURATION_NAME = "ideaPlugins"
    const val INTELLIJ_DEFAULT_DEPENDENCIES_CONFIGURATION_NAME = "intellijDefaultDependencies"

    const val ANNOTATIONS_DEPENDENCY_VERSION = "23.0.0"
    const val DEFAULT_IDEA_VERSION = "LATEST-EAP-SNAPSHOT"

    const val RELEASE_SUFFIX_EAP = "-EAP-SNAPSHOT"
    const val RELEASE_SUFFIX_EAP_CANDIDATE = "-EAP-CANDIDATE-SNAPSHOT"
    const val RELEASE_SUFFIX_SNAPSHOT = "-SNAPSHOT"
    const val RELEASE_SUFFIX_CUSTOM_SNAPSHOT = "-CUSTOM-SNAPSHOT"

    const val RELEASE_TYPE_SNAPSHOTS = "snapshots"
    const val RELEASE_TYPE_NIGHTLY = "nightly"
    const val RELEASE_TYPE_RELEASES = "releases"

    const val MARKETPLACE_HOST = "https://plugins.jetbrains.com"
    const val IDEA_PRODUCTS_RELEASES_URL = "https://www.jetbrains.com/updates/updates.xml"
    const val ANDROID_STUDIO_PRODUCTS_RELEASES_URL = "https://raw.githubusercontent.com/JetBrains/intellij-sdk-docs/main/topics/_generated/android_studio_releases.xml"
    const val CACHE_REDIRECTOR = "https://cache-redirector.jetbrains.com"
    const val INTELLIJ_DEPENDENCIES = "$CACHE_REDIRECTOR/intellij-dependencies"
    const val DEFAULT_INTELLIJ_REPOSITORY = "$CACHE_REDIRECTOR/www.jetbrains.com/intellij-repository"
    const val DEFAULT_INTELLIJ_PLUGINS_REPOSITORY = "$CACHE_REDIRECTOR/plugins.jetbrains.com/maven"
    const val DEFAULT_JBR_REPOSITORY = "$CACHE_REDIRECTOR/intellij-jbr"
    const val PLUGIN_VERIFIER_REPOSITORY =
        "$CACHE_REDIRECTOR/packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-verifier"
    const val JAVA_COMPILER_ANT_TASKS_MAVEN_METADATA =
        "$DEFAULT_INTELLIJ_REPOSITORY/releases/com/jetbrains/intellij/java/java-compiler-ant-tasks/maven-metadata.xml"
    const val GITHUB_REPOSITORY = "https://github.com/jetbrains/gradle-intellij-plugin"

    const val PLUGIN_PATH = "plugin.path"
    const val VERSION_LATEST = "latest"
    const val ANDROID_STUDIO_TYPE = "AI"
    const val PERFORMANCE_PLUGIN_ID = "com.jetbrains.performancePlugin"
}
