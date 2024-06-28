// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)
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
}

val isSnapshot = properties("snapshot").get().toBoolean()
version = when (isSnapshot) {
    true -> properties("snapshotVersion").map { "$it-SNAPSHOT" }
    false -> properties("version")
}.get()
group = properties("group").get()
description = properties("description").get()

repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

val additionalPluginClasspath: Configuration by configurations.creating

dependencies {
    implementation(libs.annotations)
    implementation(libs.undertow)

    implementation(libs.intellij.structure.base) {
        exclude("org.jetbrains.kotlin")
    }
    implementation(libs.intellij.structure.intellij) {
        exclude("org.jetbrains.kotlin")
        exclude("org.jetbrains.kotlinx")
    }
    implementation(libs.intellij.pluginRepositoryRestClient) {
        exclude("org.jetbrains.kotlin")
        exclude("org.jetbrains.kotlinx")
        exclude("org.slf4j")
    }

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xmlutil.core)
    implementation(libs.xmlutil.serialization)

    constraints {
        listOf(libs.xmlutil.core, libs.xmlutil.serialization).forEach {
            implementation(it) {
                attributes { attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm) }
            }
        }
    }

    compileOnly(embeddedKotlin("gradle-plugin"))
    additionalPluginClasspath(embeddedKotlin("gradle-plugin"))

    api(libs.retrofit)

    testImplementation(gradleTestKit())
    testImplementation(embeddedKotlin("test"))
    testImplementation(embeddedKotlin("test-junit"))

    testFixturesImplementation(gradleTestKit())
    testFixturesImplementation(embeddedKotlin("test"))
    testFixturesImplementation(embeddedKotlin("test-junit"))
    testFixturesImplementation(libs.annotations)
}

kotlin {
    jvmToolchain(17)
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion").get()
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
        enableStricterValidation.set(true)
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
            testType.set(TestSuiteType.INTEGRATION_TEST)

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
    val testGradleHome = properties("testGradleUserHome")
        .map { File(it) }
        .getOrElse(
            layout.buildDirectory.asFile
                .map { it.resolve("testGradleHome") }
                .get()
        )

    systemProperties["test.gradle.home"] = testGradleHome
    systemProperties["test.gradle.scan"] = project.gradle.startParameter.isBuildScan
    systemProperties["test.gradle.default"] = properties("gradleVersion").get()
    systemProperties["test.gradle.version"] = properties("testGradleVersion").get()
    systemProperties["test.gradle.arguments"] = properties("testGradleArguments").get()
    systemProperties["test.intellijPlatform.type"] = properties("testIntellijPlatformType").get()
    systemProperties["test.intellijPlatform.version"] = properties("testIntellijPlatformVersion").get()
    systemProperties["test.kotlin.version"] = properties("testKotlinVersion").get()
    systemProperties["test.ci"] = environment("CI").orElse("false")
    systemProperties["test.markdownPlugin.version"] = properties("testMarkdownPluginVersion").get()
    systemProperties["plugins.repository"] = properties("pluginsRepository").get()

    jvmArgs(
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

val dokkaHtml by tasks.existing(DokkaTask::class)
val javadocJar by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.map { it.outputDirectory })
    patchManifest()
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
    patchManifest()
}

artifacts {
    archives(javadocJar)
    archives(sourcesJar)
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    website.set(properties("website"))
    vcsUrl.set(properties("vcsUrl"))

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
            tags.set(properties("tags").map { it.split(',') })
        }
    }

    testSourceSets.add(sourceSets["integrationTest"])
}

publishing {
    repositories {
        maven {
            name = "snapshot"
            url = uri(properties("snapshotUrl").get())
            credentials {
                username = properties("ossrhUsername").get()
                password = properties("ossrhPassword").get()
            }
        }
    }
    publications {
        create<MavenPublication>("pluginMaven") {
            groupId = properties("group").get()
            artifactId = properties("artifactId").get()
            version = version.toString()
        }

//        create<MavenPublication>("snapshot") {
//            groupId = properties("group").get()
//            artifactId = properties("artifactId").get()
//            version = version.toString()
//
//            from(components["java"])
//
//            pom {
//                name.set("IntelliJ Platform Gradle Plugin")
//                description.set(project.description)
//                url.set(properties("website"))
//
//                packaging = "jar"
//
//                scm {
//                    connection.set(properties("scmUrl"))
//                    developerConnection.set(properties("scmUrl"))
//                    url.set(properties("vcsUrl"))
//                }
//
//                licenses {
//                    license {
//                        name.set("The Apache License, Version 2.0")
//                        url.set("https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/master/LICENSE")
//                    }
//                }
//
//                developers {
//                    developer {
//                        id.set("zolotov")
//                        name.set("Alexander Zolotov")
//                        email.set("zolotov@jetbrains.com")
//                    }
//                    developer {
//                        id.set("hsz")
//                        name.set("Jakub Chrzanowski")
//                        email.set("jakub.chrzanowski@jetbrains.com")
//                    }
//                }
//            }
//        }
    }
}

changelog {
    unreleasedTerm.set("next")
    groups.empty()
    repositoryUrl.set("https://github.com/JetBrains/intellij-platform-gradle-plugin")
}
