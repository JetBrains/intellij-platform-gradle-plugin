@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key)?.toString()

plugins {
    kotlin("jvm") version "1.5.21"
    id("java-gradle-plugin")
    id("maven-publish")
    id("com.gradle.plugin-publish") version "0.15.0"
    id("synapticloop.documentr") version "3.1.0"
    id("com.github.breadmoirai.github-release") version "2.2.12"
    id("org.jetbrains.changelog") version "1.2.0"
}

repositories {
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
}

dependencies {
    implementation("org.jetbrains:marketplace-zip-signer:0.1.5") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("org.jetbrains:annotations:21.0.0")
    implementation("org.jetbrains.intellij.plugins:structure-base:3.194") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("org.jetbrains.intellij.plugins:structure-intellij:3.194") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    // should be changed together with plugin-repository-rest-client
    implementation("org.jetbrains.intellij:blockmap:1.0.5") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("org.jetbrains.intellij:plugin-repository-rest-client:2.0.20") {
        exclude(group = "org.jetbrains.kotlin")
    }

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
**This project requires Gradle 6.6 or newer**

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
            jvmTarget = "1.8"
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
}

fun configureTests(testTask: Test) {
    val testGradleHomePath = "$buildDir/testGradleHome"
    testTask.doFirst {
        File(testGradleHomePath).mkdir()
    }
    testTask.systemProperties["test.gradle.home"] = testGradleHomePath
    testTask.systemProperties["test.kotlin.version"] = properties("kotlinVersion")
    testTask.systemProperties["test.gradle.default"] = properties("gradleVersion")
    testTask.systemProperties["test.gradle.version"] = properties("gradleVersion")
    testTask.systemProperties["plugins.repository"] = properties("pluginsRepository")
    testTask.outputs.dir(testGradleHomePath)
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.named("javadoc"))
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
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
        create<MavenPublication>("snapshot") {
            groupId = project.group.toString()
            artifactId = project.name
            version = version.toString()

            from(components["java"])
            artifact(sourcesJar.get())
            artifact(javadocJar.get())

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

githubRelease {
    val releaseNote = if (changelog.has("${project.version}")) {
        changelog.get("${project.version}").toText()
    } else {
        ""
    }

    setToken(properties("githubToken"))
    owner.set("jetbrains")
    repo.set("gradle-intellij-plugin")
    body.set(releaseNote)
}
