@file:Suppress("UnstableApiUsage")

import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key)?.toString()
fun Jar.patchManifest() = manifest { attributes("Version" to project.version) }

plugins {
    `kotlin-dsl`
    `maven-publish`
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    id("com.gradle.plugin-publish") version "0.18.0"
    id("org.jetbrains.changelog") version "1.3.1"
    id("org.jetbrains.dokka") version "1.6.0"
    id("synapticloop.documentr") version "3.1.0"
    id("com.github.breadmoirai.github-release") version "2.2.12"
}

repositories {
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    maven("https://plugins.gradle.org/m2")
}

dependencies {
    implementation("org.jetbrains:annotations:23.0.0")
    implementation("org.jetbrains.intellij.plugins:structure-base:3.201") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("org.jetbrains.intellij.plugins:structure-intellij:3.198") {
        exclude(group = "org.jetbrains.kotlin")
    }
    // should be changed together with plugin-repository-rest-client
    implementation("org.jetbrains.intellij:blockmap:2.0.16") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("org.jetbrains.intellij:plugin-repository-rest-client:2.0.20") {
        exclude(group = "org.jetbrains.kotlin")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
    implementation("javax.xml.bind:jaxb-api:2.3.1")

    api("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:1.1.1")

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

version = when (properties("snapshot")?.toBoolean() ?: false) {
    true -> "${properties("snapshotVersion")}-SNAPSHOT"
    false -> properties("version")
} ?: ""
group = "org.jetbrains.intellij.plugins"
description = """
> **Important:**
> - This project requires **Gradle 6.6** or newer, however it is recommended to **use the [latest Gradle available](https://gradle.org/releases/)**. Update it with:
>   ```bash
>   ./gradlew wrapper --gradle-version=VERSION
>   ```
> - Gradle JVM should be set to **Java 11** (see _Settings/Preferences | Build, Execution, Deployment | Build Tools | Gradle_)

When upgrading to 1.x version, please make sure to follow migration guide to adjust your existing build script: https://lp.jetbrains.com/gradle-intellij-plugin

This plugin allows you to build plugins for IntelliJ Platform using specified IntelliJ SDK and bundled/3rd-party plugins.

The plugin adds extra IntelliJ-specific dependencies, patches `processResources` tasks to fill some tags 
(name, version) in `plugin.xml` with appropriate values, patches compile tasks to instrument code with 
nullability assertions and forms classes made with IntelliJ GUI Designer and provides some build steps which might be
helpful while developing plugins for IntelliJ platform.
"""

gradlePlugin {
    plugins.create("intellijPlugin") {
        id = "org.jetbrains.intellij"
        displayName = "Gradle IntelliJ Plugin"
        implementationClass = "org.jetbrains.intellij.IntelliJPlugin"
    }
}

pluginBundle {
    website = "https://github.com/JetBrains/gradle-intellij-plugin"
    vcsUrl = "https://github.com/JetBrains/gradle-intellij-plugin"
    description = "Plugin for building plugins for IntelliJ IDEs"
    tags = listOf("intellij", "jetbrains", "idea")
}

val cacheIntolerantTest = tasks.register<Test>("cacheIntolerantTest") {
    configureTests(this)
    include("**/DownloadIntelliJSpec.class")
    filter.isFailOnNoMatchingTests = false
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            apiVersion = "1.3"
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
        distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
    }

    test {
        configureTests(this)
        exclude("**/DownloadIntelliJSpec.class")
        dependsOn(cacheIntolerantTest)
    }

    jar {
        patchManifest()
    }
}

fun configureTests(testTask: Test) {
    val testGradleHomePath = "$buildDir/testGradleHome"
    testTask.doFirst {
        File(testGradleHomePath).mkdir()
    }
    testTask.systemProperties["test.gradle.home"] = testGradleHomePath
    testTask.systemProperties["test.kotlin.version"] = properties("kotlinVersion")
    testTask.systemProperties["test.gradle.default"] = properties("gradleVersion")
    testTask.systemProperties["test.gradle.version"] = properties("testGradleVersion")
    testTask.systemProperties["test.gradle.arguments"] = properties("testGradleArguments")
    testTask.systemProperties["plugins.repository"] = properties("pluginsRepository")
    testTask.outputs.dir(testGradleHomePath)
}

val dokkaHtml by tasks.getting(DokkaTask::class)
val javadocJar by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.outputDirectory)
    patchManifest()
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
    patchManifest()
}

artifacts {
    archives(javadocJar)
    archives(sourcesJar)
}

publishing {
    repositories {
        maven {
            name = "snapshot"
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            credentials {
                username = properties("ossrhUsername")
                password = properties("ossrhPassword")
            }
        }
    }
    publications {
        withType(MavenPublication::class.java).configureEach {
            groupId = project.group.toString()
            artifactId = project.name
            version = version.toString()

            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set("Gradle IntelliJ Plugin")
                description.set(project.description)
                url.set("https://github.com/JetBrains/gradle-intellij-plugin")

                packaging = "jar"

                scm {
                    connection.set("scm:git:https://github.com/JetBrains/gradle-intellij-plugin/")
                    developerConnection.set("scm:git:https://github.com/JetBrains/gradle-intellij-plugin/")
                    url.set("https://github.com/JetBrains/gradle-intellij-plugin/")
                }

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("zolotov")
                        name.set("Alexander Zolotov")
                        email.set("zolotov@jetbrains.com")
                    }
                    developer {
                        id.set("hsz")
                        name.set("Jakub Chrzanowski")
                        email.set("jakub.chrzanowski@jetbrains.com")
                    }
                }
            }
        }
    }
}

changelog {
    unreleasedTerm.set("next")
    version.set("${project.version}")
    path.set("${project.projectDir}/CHANGES.md")
}
