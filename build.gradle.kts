plugins {
    groovy
    id("com.gradle.plugin-publish") version "0.12.0"
    id("synapticloop.documentr") version "3.1.0"
    `java-gradle-plugin`
    maven
    id("com.github.breadmoirai.github-release") version "2.2.9"
}

plugins.withType<JavaPlugin> {
    tasks.withType<GroovyCompile> {
        sourceCompatibility = "1.7"
        targetCompatibility = "1.7"
    }
}

repositories {
    maven("https://cache-redirector.jetbrains.com/jetbrains.bintray.com/intellij-plugin-service")
    maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    maven("https://cache-redirector.jetbrains.com/jcenter.bintray.com")
}

dependencies {
    implementation(localGroovy())
    api(gradleApi())
    implementation("org.jetbrains:annotations:19.0.0")
    implementation("org.jetbrains.intellij.plugins:structure-base:3.139")
    implementation("org.jetbrains.intellij.plugins:structure-intellij:3.139")
    implementation("org.jetbrains.intellij:plugin-repository-rest-client:2.0.15") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("de.undercouch:gradle-download-task:4.0.4")

    testImplementation(gradleTestKit())
    testImplementation("org.spockframework:spock-core:1.0-groovy-2.4") {
        exclude(module = "groovy-all")
    }
    testImplementation("junit:junit:4.12")
}

version = "0.4.25"
group = "org.jetbrains.intellij.plugins"
description = """
**This project requires Gradle 4.9 or newer**

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

tasks.test {
    configureTests(this)
    exclude("**/DownloadIntelliJSpec.class")
    dependsOn(cacheIntolerantTest)
}

fun configureTests(testTask: Test) {
    val testGradleHomePath = "$buildDir/testGradleHome"
    testTask.doFirst {
        File(testGradleHomePath).mkdir()
    }
    testTask.systemProperties["test.gradle.home"] = testGradleHomePath
    testTask.systemProperties["plugins.repo"] = project.property("pluginsRepo")
    testTask.outputs.dir(testGradleHomePath)
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    classifier = "javadoc"
    from(tasks.named("javadoc"))
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    classifier = "sources"
    from(sourceSets.main.get().allSource)
}

artifacts {
    archives(javadocJar)
    archives(sourcesJar)
}

tasks.named<Upload>("uploadArchives") {
    repositories.withGroovyBuilder {
        "mavenDeployer" {
            "snapshotRepository"("url" to "https://oss.sonatype.org/content/repositories/snapshots/") {
                "authentication"(
                        "userName" to project.property("ossrhUsername"),
                        "password" to project.property("ossrhPassword")
                )
            }

            "pom" {
                "project" {
                    setProperty("artifactId", project.name)
                    setProperty("name", "Gradle IntelliJ Plugin")
                    setProperty("description", project.description)
                    setProperty("version", "${project.property("snapshotVersion")}-SNAPSHOT")
                    setProperty("url", "https://github.com/JetBrains/gradle-intellij-plugin")
                    setProperty("packaging", "jar")

                    "scm" {
                        setProperty("connection", "scm:git:https://github.com/JetBrains/gradle-intellij-plugin/")
                        setProperty("developerConnection", "scm:git:https://github.com/JetBrains/gradle-intellij-plugin/")
                        setProperty("url", "https://github.com/JetBrains/gradle-intellij-plugin/")
                    }

                    "licenses" {
                        "license" {
                            setProperty("name", "The Apache License, Version 2.0")
                            setProperty("url", "http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    "developers" {
                        "developer" {
                            setProperty("id", "zolotov")
                            setProperty("name", "Alexander Zolotov")
                            setProperty("email", "zolotov@jetbrains.com")
                        }
                    }
                }
            }
        }
    }
}

tasks.wrapper {
    gradleVersion = "6.3"
    distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-${gradleVersion}-all.zip"
}

githubRelease {
    setToken(project.property("githubToken") as String)
    owner.set("jetbrains")
    repo.set("gradle-intellij-plugin")
    body.set(extractChanges().trim())
}

fun extractChanges(): String {
    val currentVersionTitle = "## ${project.version}"
    val changes = file("CHANGES.md").readText()
    val startOffset = changes.indexOf(currentVersionTitle) + currentVersionTitle.length
    if (startOffset == -1) return ""
    val endOffset = changes.indexOf("\n## ", startOffset)
    return if (endOffset >= 0) {
        changes.substring(startOffset, endOffset)
    } else {
        changes.substring(startOffset)
    }
}
