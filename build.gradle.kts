// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun Jar.patchManifest() = manifest { attributes("Version" to project.version) }

plugins {
    `jvm-test-suite`
    `java-test-fixtures`
    `kotlin-dsl`
    `maven-publish`
    kotlin("plugin.serialization") version embeddedKotlinVersion
    alias(libs.plugins.pluginPublish)
    alias(libs.plugins.changelog)
    alias(libs.plugins.dokka)
    alias(libs.plugins.bcv)
    alias(libs.plugins.buildLogic)
}

val isSnapshot = providers.gradleProperty("snapshot").get().toBoolean()
version = when (isSnapshot) {
    true -> providers.gradleProperty("snapshotVersion").map { "$it-SNAPSHOT" }
    false -> providers.gradleProperty("version")
}.get()
group = providers.gradleProperty("group").get()
description = providers.gradleProperty("description").get()

repositories {
    mavenCentral()
}

val additionalPluginClasspath: Configuration by configurations.creating

dependencies {
    api(libs.undertow)

    val commonExclusions: Action<ExternalModuleDependency> = Action {
        exclude("org.jetbrains.kotlin")
        exclude("org.jetbrains.kotlinx")
        exclude("org.slf4j")
    }
    implementation(libs.intellij.structure.base, commonExclusions)
    api(libs.intellij.structure.ide, commonExclusions)
    api(libs.intellij.structure.intellij, commonExclusions)
    api(libs.intellij.pluginRepositoryRestClient, commonExclusions)

    runtimeOnly(libs.xmlutil.core)
    api(libs.xmlutil.serialization) {
        exclude("io.github.pdvrieze.xmlutil", "core")
    }
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    compileOnly(embeddedKotlin("gradle-plugin"))
    additionalPluginClasspath(embeddedKotlin("gradle-plugin"))

    api(libs.okhttp)
    api(libs.retrofit)

    testImplementation(gradleTestKit())
    testImplementation(embeddedKotlin("test"))
    testImplementation(embeddedKotlin("test-junit"))

    testFixturesImplementation(gradleTestKit())
    testFixturesImplementation(embeddedKotlin("test"))
    testFixturesImplementation(embeddedKotlin("test-junit"))
}

kotlin {
    jvmToolchain(17)
}

tasks {
    withType<KotlinCompile>().configureEach {
        compilerOptions {
            apiVersion.set(KotlinVersion.KOTLIN_1_8)
            languageVersion.set(KotlinVersion.KOTLIN_1_8)
        }
    }

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
        distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
    }

    pluginUnderTestMetadata {
        pluginClasspath.from(additionalPluginClasspath)
    }

    test {
        configureTests()
    }

    jar {
        patchManifest()
    }

    validatePlugins {
        enableStricterValidation = true
    }

//    @Suppress("UnstableApiUsage")
//    check {
//        dependsOn(testing.suites.getByName("integrationTest")) // TODO: run after `test`?
//    }
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        fun JvmComponentDependencies.embeddedKotlin(module: String) =
            project.dependencies.embeddedKotlin(module) as String

//        named<JvmTestSuite>("test") {
//            dependencies {
//                implementation(project())
//                implementation(testFixtures(project()))
//            }
//        }

        register<JvmTestSuite>("integrationTest") {
            useJUnit()
//            testType = TestSuiteType.INTEGRATION_TEST

            dependencies {
                implementation(project())
                implementation(gradleTestKit())
                implementation(testFixtures(project()))
                implementation(embeddedKotlin("test"))
                implementation(embeddedKotlin("test-junit"))
            }

            targets {
                all {
                    testTask.configure {
                        configureTests()
                    }
                }
            }
        }
    }
}

fun Test.configureTests() {
    val testGradleHome = providers.gradleProperty("testGradleUserHome")
        .map { File(it) }
        .getOrElse(
            layout.buildDirectory.asFile
                .map { it.resolve("testGradleHome") }
                .get()
        )

    systemProperties["test.gradle.home"] = testGradleHome
    systemProperties["test.gradle.scan"] = project.gradle.startParameter.isBuildScan
    systemProperties["test.gradle.default"] = providers.gradleProperty("gradleVersion").get()
    systemProperties["test.gradle.version"] = providers.gradleProperty("testGradleVersion").map { gradleVersion ->
        when (gradleVersion) {
            "nightly" -> gradleNightlyVersion()
            else -> gradleVersion
        }
    }.get()
    systemProperties["test.gradle.arguments"] = providers.gradleProperty("testGradleArguments").get()
    systemProperties["test.intellijPlatform.type"] = providers.gradleProperty("testIntellijPlatformType").get()
    systemProperties["test.intellijPlatform.version"] = providers.gradleProperty("testIntellijPlatformVersion").get()
    systemProperties["test.kotlin.version"] = providers.gradleProperty("testKotlinVersion").get()
    systemProperties["test.markdownPlugin.version"] = providers.gradleProperty("testMarkdownPluginVersion").get()

    jvmArgs(
        "-Xmx4G",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
    )

    // Verbose tests output used for debugging tasks:
    //   testLogging {
    //       outputs.upToDateWhen { false }
    //       showStandardStreams = true
    //   }

    outputs.dir(testGradleHome)
}

val dokkaGeneratePublicationHtml by tasks.existing(DokkaGeneratePublicationTask::class)
val javadocJar by tasks.registering(Jar::class) {
    dependsOn(dokkaGeneratePublicationHtml)
    archiveClassifier = "javadoc"
    from(dokkaGeneratePublicationHtml.map { it.outputDirectory })
    patchManifest()
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier = "sources"
    from(sourceSets.main.get().allSource)
    patchManifest()
}

artifacts {
    archives(javadocJar)
    archives(sourcesJar)
}

gradlePlugin {
    website = providers.gradleProperty("website")
    vcsUrl = providers.gradleProperty("vcsUrl")

    mapOf(
        // main plugins
        "" to "project.IntelliJPlatformPlugin",
        "migration" to "project.IntelliJPlatformMigrationPlugin",
        "module" to "project.IntelliJPlatformModulePlugin",
        "settings" to "settings.IntelliJPlatformSettingsPlugin",
        "base" to "project.IntelliJPlatformBasePlugin",
    ).forEach { (pluginId, pluginClass) ->
        plugins.create("intellijPlatform${pluginId.replaceFirstChar { it.titlecase() }}") {
            id = "org.jetbrains.intellij.platform" + ".$pluginId".takeIf { pluginId.isNotBlank() }.orEmpty()
            displayName = "IntelliJ Platform Gradle Plugin" + " ($pluginId)".takeIf { pluginId.isNotBlank() }.orEmpty()
            implementationClass = "org.jetbrains.intellij.platform.gradle.plugins.$pluginClass"
            description = project.description
            tags = providers.gradleProperty("tags").map { it.split(',') }
        }
    }

    testSourceSets.add(sourceSets["integrationTest"])
}

publishing {
    repositories {
        maven {
            name = "snapshot"
            url = uri(providers.gradleProperty("snapshotUrl").get())
            credentials {
                username = providers.gradleProperty("ossrhUsername").get()
                password = providers.gradleProperty("ossrhPassword").get()
            }
        }
    }
    publications {
        create<MavenPublication>("pluginMaven") {
            groupId = providers.gradleProperty("group").get()
            artifactId = providers.gradleProperty("artifactId").get()
            version = version.toString()
        }
    }
}

changelog {
    unreleasedTerm = "next"
    groups.empty()
    repositoryUrl = "https://github.com/JetBrains/intellij-platform-gradle-plugin"
}
