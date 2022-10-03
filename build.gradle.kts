// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key)?.toString()
fun Jar.patchManifest() = manifest { attributes("Version" to project.version) }

plugins {
    `kotlin-dsl`
    `maven-publish`
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"
    id("com.gradle.plugin-publish") version "1.0.0"
    id("org.jetbrains.changelog") version "1.3.1"
    id("org.jetbrains.dokka") version "1.7.10"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.11.1"
}

version = when (properties("snapshot")?.toBoolean() ?: false) {
    true -> "${properties("snapshotVersion")}-SNAPSHOT"
    false -> properties("version")
}.orEmpty()
group = properties("group")!!
description = properties("description")!!

repositories {
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    maven("https://plugins.gradle.org/m2")
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:annotations:23.0.0")
    implementation("org.jetbrains.intellij.plugins:structure-base:3.238") {
        exclude("org.jetbrains.kotlin")
    }
    implementation("org.jetbrains.intellij.plugins:structure-intellij:3.238") {
        exclude("org.jetbrains.kotlin")
    }
    implementation("org.jetbrains.intellij:plugin-repository-rest-client:2.0.28") {
        exclude("org.jetbrains.kotlin")
        exclude("org.slf4j")
    }

    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0")
    implementation("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
    implementation("com.googlecode.plist:dd-plist:1.25")

    api("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:1.1.6")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
    api("com.squareup.retrofit2:retrofit:2.9.0")

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

gradlePlugin {
    plugins.create("intellijPlugin") {
        id = properties("pluginId")
        displayName = properties("pluginDisplayName")
        implementationClass = properties("pluginImplementationClass")
        description = project.description
    }
}

pluginBundle {
    website = properties("website")
    vcsUrl = properties("vcsUrl")
    tags = properties("tags")?.split(',')
    description = project.description
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            apiVersion = "1.4"
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
        distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
    }

    test {
        val testGradleHomePath = properties("testGradleUserHome") ?: "$buildDir/testGradleHome"
        doFirst {
            File(testGradleHomePath).mkdir()
        }
        systemProperties["test.gradle.home"] = testGradleHomePath
        systemProperties["test.kotlin.version"] = properties("kotlinVersion")
        systemProperties["test.gradle.default"] = properties("gradleVersion")
        systemProperties["test.gradle.version"] = properties("testGradleVersion")
        systemProperties["test.gradle.arguments"] = properties("testGradleArguments")
        systemProperties["test.intellij.version"] = properties("testIntelliJVersion")
        systemProperties["test.markdownPlugin.version"] = properties("testMarkdownPluginVersion")
        systemProperties["plugins.repository"] = properties("pluginsRepository")
        outputs.dir(testGradleHomePath)
    }

    jar {
        patchManifest()
    }
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
            url = uri(properties("snapshotUrl")!!)
            credentials {
                username = properties("ossrhUsername")
                password = properties("ossrhPassword")
            }
        }
    }
    publications {
        create<MavenPublication>("snapshot") {
            groupId = properties("pluginId")
            artifactId = properties("artifactId")
            version = version.toString()

            from(components["java"])

            pom {
                name.set(properties("pluginDisplayName"))
                description.set(project.description)
                url.set(properties("website"))

                packaging = "jar"

                scm {
                    connection.set(properties("scmUrl"))
                    developerConnection.set(properties("scmUrl"))
                    url.set(properties("vcsUrl"))
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
    groups.set(emptyList())
}
