// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.JavaPlugin
import org.gradle.util.GradleVersion
import org.jetbrains.intellij.platform.gradle.utils.toVersion

object Constants {
    const val CACHE_DIRECTORY = ".intellijPlatform"
    const val CACHE_DIRECTORY_IVY = "localPlatformArtifacts"

    object Plugin {
        const val ID = "org.jetbrains.intellij.platform"
        const val NAME = "IntelliJ Platform Gradle Plugin"
        const val GROUP_NAME = "intellij platform"
    }

    object Plugins {
        const val BASE = "${Plugin.ID}.base"
        const val MODULE = "${Plugin.ID}.module"
        const val SETTINGS = "${Plugin.ID}.settings"

        object External {
            const val IDEA = "idea"
            const val JAVA_TEST_FIXTURES = "java-test-fixtures"
            const val KOTLIN = "org.jetbrains.kotlin.jvm"
        }
    }

    object Constraints {
        const val CLOSEST_VERSION = "closest"
        const val LATEST_VERSION = "latest"
        const val PLATFORM_VERSION = "platform"
        val MINIMAL_GRADLE_VERSION: GradleVersion = GradleVersion.version("8.5")
        val MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER = "223".toVersion()
        val MINIMAL_INTELLIJ_PLATFORM_VERSION = "2022.3".toVersion()
        val MINIMAL_SPLIT_MODE_BUILD_NUMBER = "241.14473".toVersion()
    }

    object Extensions {
        const val IDES = "ides"
        const val IDEA_VERSION = "ideaVersion"
        const val INTELLIJ_PLATFORM = "intellijPlatform"
        const val INTELLIJ_PLATFORM_TESTING = "intellijPlatformTesting"
        const val PLUGIN_CONFIGURATION = "pluginConfiguration"
        const val PLUGIN_VERIFICATION = "pluginVerification"
        const val PLUGINS = "plugins"
        const val PRODUCT_DESCRIPTOR = "productDescriptor"
        const val PUBLISHING = "publishing"
        const val SIGNING = "signing"
        const val VENDOR = "vendor"
    }

    object Components {
        const val INTELLIJ_PLATFORM = "intellijPlatform"
    }

    /**
     * See:
     * - [Variant-aware sharing of artifacts between projects](https://docs.gradle.org/current/userguide/cross_project_publications.html#sec:variant-aware-sharing)
     */
    object Configurations {
        const val INTELLIJ_PLATFORM_COMPOSED_JAR = "intellijPlatformComposedJar"
        const val INTELLIJ_PLATFORM_COMPOSED_JAR_API = "intellijPlatformComposedJarApi"
        const val INTELLIJ_PLATFORM_DEPENDENCY_ARCHIVE = "intellijPlatformDependencyArchive"
        const val INTELLIJ_PLATFORM_DISTRIBUTION = "intellijPlatformDistribution"
        const val INTELLIJ_PLATFORM_LOCAL = "intellijPlatformLocal"
        const val INTELLIJ_PLATFORM_DEPENDENCY = "intellijPlatformDependency"
        const val INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY = "intellijPlatformPluginDependency"
        const val INTELLIJ_PLATFORM_PLUGIN_LOCAL = "intellijPlatformPluginLocal"
        const val INTELLIJ_PLATFORM_PLUGIN_MODULE = "intellijPlatformPluginModule"
        const val INTELLIJ_PLATFORM_PLUGIN = "intellijPlatformPlugin"
        const val INTELLIJ_PLATFORM_BUNDLED_PLUGINS = "intellijPlatformBundledPlugins"
        const val INTELLIJ_PLATFORM_BUNDLED_MODULES = "intellijPlatformBundledModules"
        const val INTELLIJ_PLATFORM_CLASSPATH = "intellijPlatformClasspath"
        const val INTELLIJ_PLATFORM_DEPENDENCIES = "intellijPlatformDependencies"

        const val INTELLIJ_PLATFORM_TEST_DEPENDENCIES = "intellijPlatformTestDependencies"
        const val INTELLIJ_PLATFORM_TEST_CLASSPATH = "intellijPlatformTestClasspath"
        const val INTELLIJ_PLATFORM_TEST_PLUGIN_DEPENDENCY = "intellijPlatformTestPluginDependency"
        const val INTELLIJ_PLATFORM_TEST_PLUGIN_LOCAL = "intellijPlatformTestPluginLocal"
        const val INTELLIJ_PLATFORM_TEST_PLUGIN = "intellijPlatformTestPlugin"
        const val INTELLIJ_PLATFORM_TEST_RUNTIME_CLASSPATH = "intellijPlatformTestRuntimeClasspath"
        const val INTELLIJ_PLATFORM_TEST_RUNTIME_FIX_CLASSPATH = "intellijPlatformTestRuntimeFixClasspath"
        const val INTELLIJ_PLATFORM_TEST_BUNDLED_PLUGINS = "intellijPlatformTestBundledPlugins"
        const val INTELLIJ_PLATFORM_TEST_BUNDLED_MODULES = "intellijPlatformTestBundledModules"

        const val INTELLIJ_PLATFORM_JAVA_COMPILER = "intellijPlatformJavaCompiler"
        const val INTELLIJ_PLATFORM_RUNTIME_CLASSPATH = "intellijPlatformRuntimeClasspath"
        const val INTELLIJ_PLUGIN_VERIFIER = "intellijPluginVerifier"
        const val INTELLIJ_PLUGIN_VERIFIER_IDES = "intellijPluginVerifierIdes"
        const val INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY = "intellijPluginVerifierIdesDependency"
        const val INTELLIJ_PLUGIN_VERIFIER_IDES_LOCAL_INSTANCE = "intellijPluginVerifierIdesLocalInstance"
        const val MARKETPLACE_ZIP_SIGNER = "marketplaceZipSigner"
        const val JETBRAINS_RUNTIME = "jetbrainsRuntime"
        const val JETBRAINS_RUNTIME_DEPENDENCY = "jetbrainsRuntimeDependency"
        const val JETBRAINS_RUNTIME_LOCAL_INSTANCE = "jetbrainsRuntimeLocalInstance"

        object Attributes {
            const val COMPOSED_JAR_NAME = "composed-jar"
            const val DISTRIBUTION_NAME = "distribution"

            val localPluginsNormalized = Attribute.of("intellijPlatformLocalPluginsNormalized", Boolean::class.javaObjectType)
            val collected = Attribute.of("intellijPlatformCollected", Boolean::class.javaObjectType)
            val extracted = Attribute.of("intellijPlatformExtracted", Boolean::class.javaObjectType)
            val jvmEnvironment = Attribute.of("org.gradle.jvm.environment", String::class.java)
            val kotlinJPlatformType = Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java)

            enum class ArtifactType {
                DIRECTORY, DMG, TAR_GZ, SIT, ZIP;

                override fun toString() = name.replace('_', '.').lowercase()

                companion object {
                    val Archives = enumValues<ArtifactType>().toList() - DIRECTORY

                    fun from(value: String) = enumValues<ArtifactType>().find { it.toString() == value }
                }
            }
        }

        object Dependencies {
            const val LOCAL_IDE_GROUP = "localIde"
            const val LOCAL_PLUGIN_GROUP = "localPlugin"
            const val LOCAL_JETBRAINS_RUNTIME_GROUP = "localJetBrainsRuntime"
            const val BUNDLED_MODULE_GROUP = "bundledModule"
            const val BUNDLED_PLUGIN_GROUP = "bundledPlugin"
            const val MARKETPLACE_GROUP = "com.jetbrains.plugins"
        }

        /**
         * See:
         * - [The Java Library plugin configurations](https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph)
         * - [The Java plugin configurations](https://docs.gradle.org/current/userguide/java_plugin.html#resolvable_configurations)
         */
        object External {
            const val API = JvmConstants.API_CONFIGURATION_NAME
            const val COMPILE_CLASSPATH = JvmConstants.COMPILE_CLASSPATH_CONFIGURATION_NAME
            const val COMPILE_ONLY = JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME
            const val COMPILE_ONLY_API = JvmConstants.COMPILE_ONLY_API_CONFIGURATION_NAME
            const val IMPLEMENTATION = JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME
            const val RUNTIME_CLASSPATH = JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
            const val RUNTIME_ELEMENTS = JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME
            const val RUNTIME_ONLY = JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME
            const val TEST_COMPILE_CLASSPATH = JvmConstants.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME
            const val TEST_COMPILE_ONLY = JvmConstants.TEST_COMPILE_ONLY_CONFIGURATION_NAME
            const val TEST_RUNTIME_CLASSPATH = JvmConstants.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME
            const val TEST_FIXTURES_COMPILE_ONLY = "testFixturesCompileOnly"
            const val TEST_FIXTURES_COMPILE_CLASSPATH = "testFixturesCompileClasspath"
        }
    }

    object Tasks {
        const val BUILD_PLUGIN = "buildPlugin"
        const val BUILD_SEARCHABLE_OPTIONS = "buildSearchableOptions"
        const val COMPOSED_JAR = "composedJar"
        const val GENERATE_MANIFEST = "generateManifest"
        const val INITIALIZE_INTELLIJ_PLATFORM_PLUGIN = "initializeIntellijPlatformPlugin"
        const val INSTRUMENT_CODE = "instrumentCode"
        const val INSTRUMENT_TEST_CODE = "instrumentTestCode"
        const val INSTRUMENTED_JAR = "instrumentedJar"
        const val JAR_SEARCHABLE_OPTIONS = "jarSearchableOptions"
        const val PATCH_PLUGIN_XML = "patchPluginXml"
        const val PREPARE_JAR_SEARCHABLE_OPTIONS = "prepareJarSearchableOptions"
        const val PREPARE_SANDBOX = "prepareSandbox"
        const val PREPARE_TEST = "prepareTest"
        const val PREPARE_TEST_SANDBOX = "prepareTestSandbox"
        const val PREPARE_TEST_IDE_PERFORMANCE_SANDBOX = "prepareTestIdePerformanceSandbox" // TODO: check
        const val PRINT_BUNDLED_PLUGINS = "printBundledPlugins"
        const val PRINT_PRODUCTS_RELEASES = "printProductsReleases"
        const val PUBLISH_PLUGIN = "publishPlugin"
        const val RUN_IDE = "runIde"
        const val SETUP_DEPENDENCIES = "setupDependencies"
        const val SIGN_PLUGIN = "signPlugin"
        const val TEST_IDE_PERFORMANCE = "testIdePerformance" // TODO: check
        const val VERIFY_PLUGIN = "verifyPlugin"
        const val VERIFY_PLUGIN_STRUCTURE = "verifyPluginStructure"
        const val VERIFY_PLUGIN_PROJECT_CONFIGURATION = "verifyPluginProjectConfiguration"
        const val VERIFY_PLUGIN_SIGNATURE = "verifyPluginSignature"

        object External {
            const val CLEAN = "clean"
            const val COMPILE_JAVA = JavaPlugin.COMPILE_JAVA_TASK_NAME
            const val COMPILE_KOTLIN = "compileKotlin"
            const val JAR = JavaPlugin.JAR_TASK_NAME
            const val TEST = JavaPlugin.TEST_TASK_NAME
        }
    }

    object Sandbox {
        const val CONTAINER = "idea-sandbox"
        const val CONFIG = "config"
        const val PLUGINS = "plugins"
        const val SYSTEM = "system"
        const val LOG = "log"

        object Plugin {
            const val LIB = "lib"
            const val MODULES = "modules"
            const val LIB_MODULES = "$LIB/$MODULES"
        }
    }

    object Locations {
        const val CACHE_REDIRECTOR = "https://cache-redirector.jetbrains.com"
        const val CACHE_REDIRECTOR_INTELLIJ_DEPENDENCIES_REPOSITORY = "$CACHE_REDIRECTOR/intellij-dependencies"
        const val CACHE_REDIRECTOR_INTELLIJ_REPOSITORY_NIGHTLY = "$CACHE_REDIRECTOR/www.jetbrains.com/intellij-repository/nightly"
        const val CACHE_REDIRECTOR_INTELLIJ_REPOSITORY_RELEASES = "$CACHE_REDIRECTOR/www.jetbrains.com/intellij-repository/releases"
        const val CACHE_REDIRECTOR_INTELLIJ_REPOSITORY_SNAPSHOTS = "$CACHE_REDIRECTOR/www.jetbrains.com/intellij-repository/snapshots"
        const val CACHE_REDIRECTOR_JETBRAINS_RUNTIME_REPOSITORY = "$CACHE_REDIRECTOR/intellij-jbr"
        const val CACHE_REDIRECTOR_MARKETPLACE_REPOSITORY = "$CACHE_REDIRECTOR/plugins.jetbrains.com/maven"

        const val INTELLIJ_DEPENDENCIES_REPOSITORY = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies"
        const val INTELLIJ_REPOSITORY_NIGHTLY = "https://www.jetbrains.com/intellij-repository/nightly"
        const val INTELLIJ_REPOSITORY_RELEASES = "https://www.jetbrains.com/intellij-repository/releases"
        const val INTELLIJ_REPOSITORY_SNAPSHOTS = "https://www.jetbrains.com/intellij-repository/snapshots"
        const val MARKETPLACE_REPOSITORY = "https://plugins.jetbrains.com/maven"

        const val GITHUB_REPOSITORY = "https://github.com/jetbrains/intellij-platform-gradle-plugin"
        const val MAVEN_GRADLE_PLUGIN_PORTAL_REPOSITORY = "https://plugins.gradle.org/m2"
        const val MAVEN_REPOSITORY = "https://repo1.maven.org/maven2"
        const val JETBRAINS_MARKETPLACE = "https://plugins.jetbrains.com"

        const val ANDROID_STUDIO_INSTALLERS = "https://redirector.gvt1.com/edgedl/android/studio"
        const val JETBRAINS_IDES_INSTALLERS = "https://download.jetbrains.com"
        const val PRODUCTS_RELEASES_ANDROID_STUDIO = "https://jb.gg/android-studio-releases-list.xml"
        const val PRODUCTS_RELEASES_JETBRAINS_IDES = "https://www.jetbrains.com/updates/updates.xml"
    }
}
