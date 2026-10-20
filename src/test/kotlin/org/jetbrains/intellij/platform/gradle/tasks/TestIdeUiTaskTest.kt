// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertFalse

class TestIdeUiTaskTest : IntelliJPluginTestBase() {

    override fun intellijPlatformDependency() = """local("${createLocalIde("local-ide").invariantSeparatorsPathString}")"""

    private fun createLocalIde(name: String, build: String = "262.12345.1") = dir.resolve(name).createDirectories().also { platform ->
        platform.resolve("product-info.json") write """
            {
                "name": "IntelliJ IDEA", "version": "2026.2.3",
                "buildNumber": "$build", "productCode": "IU",
                "dataDirectoryName": "IntelliJIdea2026.2", "productVendor": "JetBrains",
                "svgIconPath": "bin/idea.svg",
                "modules": [], "bundledPlugins": [],
                "layout": [{"name": "com.intellij", "kind": "plugin", "classPath": ["lib/$name.jar"]}]
            }
        """.trimIndent()
        platform.resolve("build.txt") write "IU-$build"
        platform.resolve("plugins").createDirectories()
        platform.resolve("lib").createDirectories()
        ZipOutputStream(platform.resolve("lib/$name.jar").outputStream()).use {
            it.putNextEntry(ZipEntry("META-INF/plugin.xml"))
            it.write("<idea-plugin><id>com.intellij</id><name>IDE</name><version>1.0</version><vendor>JetBrains</vendor></idea-plugin>".toByteArray())
        }
        platform.resolve("modules").createDirectories()
        ZipOutputStream(platform.resolve("modules/module-descriptors.jar").outputStream()).use { }
    }

    @Test
    fun `UI test defaults are registered and isolated`() {
        // Keeps the fixture offline: the resolvable configurations drop Starter (and its JUnit), leaving the IDE classpath visible.
        val hostGroups = TestFrameworkType.Starter.coordinates.map { it.groupId }.toSet()
        buildFile write """
            val integration = sourceSets.getByName("testIdeUi")
            check(sourceSets.findByName("integrationTest") == null)
            check(integration.implementationConfigurationName == "testIdeUiImplementation")
            check(integration.runtimeOnlyConfigurationName == "testIdeUiRuntimeOnly")
            check(integration.compileClasspathConfigurationName == "testIdeUiCompileClasspath")
            check(integration.runtimeClasspathConfigurationName == "testIdeUiRuntimeClasspath")
            listOf(integration.compileClasspathConfigurationName, integration.runtimeClasspathConfigurationName).forEach { name ->
                ${hostGroups.joinToString("\n                ") { """configurations.getByName(name).exclude(mapOf("group" to "$it"))""" }}
            }
            val ui = tasks.named<TestIdeUiTask>("testIdeUi").get()
            val ideClasspath = configurations.getByName("intellijPlatformClasspath").files.toList()
            check(ideClasspath.any { it.name == "local-ide.jar" })
            check(configurations.getByName(integration.runtimeClasspathConfigurationName).files.isEmpty())
            check(integration.compileClasspath.files.toList() == ideClasspath)
            check(ui.testClassesDirs == integration.output.classesDirs)
            check(ui.classpath.files.toList() == integration.runtimeClasspath.files.toList() + ideClasspath)
            check(ui.classpath.files.toList().takeLast(ideClasspath.size) == ideClasspath)
            check(ui.options is org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions)
            check((ui.options as org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions).includeEngines.isEmpty())
            check((ui.options as org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions).excludeEngines.isEmpty())
            check(integration.java.srcDirs.contains(file("src/testIdeUi/java")))
            check(integration.resources.srcDirs.contains(file("src/testIdeUi/resources")))
            check(integration.allSource.srcDirs.contains(file("src/testIdeUi/kotlin")))
            check(ui.archiveFile.get() == tasks.named<BuildPluginTask>("buildPlugin").get().archiveFile.get())
            val implementation = configurations.getByName(integration.implementationConfigurationName)
            check(configurations.getByName("testImplementation") !in implementation.hierarchy)
            val host = implementation.allDependencies.filterIsInstance<org.gradle.api.artifacts.ExternalModuleDependency>()
            check(host.any { it.name == "ide-starter-squashed" })
            check(host.any { it.name == "driver-client" })
            check(host.filter { it.group?.startsWith("com.jetbrains.intellij") == true }.all { it.excludeRules.isNotEmpty() })
            check(host.filter { it.group?.startsWith("com.jetbrains.intellij") == true }.all { dependency -> dependency.excludeRules.any { it.group == "junit" && it.module == "junit" } })
            check(host.all { dependency -> dependency.excludeRules.none { it.group == "org.junit.vintage" } })
            check(host.none { it.group == "junit" || it.group?.startsWith("org.junit") == true })
            check(configurations.getByName(integration.runtimeOnlyConfigurationName).allDependencies.none { it.group == "junit" || it.group?.startsWith("org.junit") == true })
            check(configurations.getByName("implementation").allDependencies.none { it.name.contains("starter") || it.name.contains("jupiter") })
            check(configurations.getByName("testImplementation").allDependencies.none { it.name.contains("starter") })
            check("integrationTest" !in tasks.names)
            println("UI_DEFAULTS_OK")
        """.trimIndent()

        build("build", "--dry-run") {
            assertContains("UI_DEFAULTS_OK", output)
            assertFalse(output.contains(":testIdeUi"))
            assertContains(":test SKIPPED", output)
            assertContains(":check SKIPPED", output)
        }
    }

    @Test
    fun `UI test defaults allow overrides and extra testing registrations`() {
        buildFile write """
            sourceSets.named("testIdeUi") {
                java.setSrcDirs(listOf("custom-ui/java"))
                resources.setSrcDirs(listOf("custom-ui/resources"))
            }
            tasks.named<TestIdeUiTask>("testIdeUi") {
                archiveFile.set(layout.projectDirectory.file("custom.zip"))
                classpath = files("custom-host.jar")
                testClassesDirs = files("custom-classes")
                systemProperty("ide.test.version", "explicit-version")
            }
            intellijPlatformTesting.testIdeUi.register("extraUi") {
                task { classpath = files("extra-host.jar") }
            }
            val ui = tasks.named<TestIdeUiTask>("testIdeUi").get()
            check(ui.archiveFile.get().asFile == file("custom.zip"))
            check(ui.classpath.files == setOf(file("custom-host.jar")))
            check(ui.testClassesDirs.files == setOf(file("custom-classes")))
            check(ui.systemProperties["ide.test.version"] == "explicit-version")
            check(sourceSets.getByName("testIdeUi").java.srcDirs == setOf(file("custom-ui/java")))
            check(tasks.named<TestIdeUiTask>("extraUi").get().classpath.files == setOf(file("extra-host.jar")))
            println("UI_OVERRIDES_OK")
        """.trimIndent()

        build("help") { assertContains("UI_OVERRIDES_OK", output) }
    }

    @Test
    fun `UI tests leave unrelated integration tests isolated`() {
        buildFile write """
            val integration = sourceSets.create("integrationTest") {
                java.setSrcDirs(listOf("custom-integration/java"))
                compileClasspath = files("integration-compile.jar")
                runtimeClasspath = files("integration-runtime.jar")
            }
            val ui = sourceSets.getByName("testIdeUi")
            check(integration.java.srcDirs == setOf(file("custom-integration/java")))
            check(integration.compileClasspath.files == setOf(file("integration-compile.jar")))
            check(integration.runtimeClasspath.files == setOf(file("integration-runtime.jar")))
            val integrationImplementation = configurations.getByName(integration.implementationConfigurationName)
            val uiImplementation = configurations.getByName(ui.implementationConfigurationName)
            check(integrationImplementation.allDependencies.isEmpty())
            check(integrationImplementation !in uiImplementation.hierarchy)
            check(uiImplementation !in integrationImplementation.hierarchy)
            check(tasks.named<TestIdeUiTask>("testIdeUi").get().testClassesDirs == ui.output.classesDirs)
            check("integrationTest" !in tasks.names)
            println("UI_INTEGRATION_ISOLATION_OK")
        """.trimIndent()

        build("help") { assertContains("UI_INTEGRATION_ISOLATION_OK", output) }
    }

    @Test
    fun `custom UI test registration appends its own IntelliJ Platform classpath`() {
        val secondIde = createLocalIde("second-ide", build = "262.12345.2")
        val hostGroups = TestFrameworkType.Starter.coordinates.map { it.groupId }.toSet()
        buildFile write """
            sourceSets.named("testIdeUi") {
                runtimeClasspath = files("default-host.jar")
            }
            val custom = sourceSets.create("extraUi") {
                java.setSrcDirs(listOf("custom-ui/java"))
                runtimeClasspath = files("extra-host.jar")
            }
            intellijPlatformTesting.testIdeUi.register("extraUi") {
                localPath = file("${secondIde.invariantSeparatorsPathString}")
            }
            ${hostGroups.joinToString("\n            ") { """configurations.getByName(custom.compileClasspathConfigurationName).exclude(mapOf("group" to "$it"))""" }}
            val extra = tasks.named<TestIdeUiTask>("extraUi").get()
            check(extra.testClassesDirs == custom.output.classesDirs)
            check(extra.testClassesDirs != sourceSets.getByName("testIdeUi").output.classesDirs)
            check(custom.java.srcDirs == setOf(file("custom-ui/java")))
            val ideClasspath = configurations.getByName("intellijPlatformClasspath_extraUi").files.toList()
            check(custom.compileClasspath.files.toList() == ideClasspath)
            val files = extra.classpath.files.toList()
            check(files.first() == file("extra-host.jar"))
            check(files.any { it.name == "second-ide.jar" })
            check(files.none { it.name == "local-ide.jar" || it.name == "default-host.jar" })
            check(files == listOf(file("extra-host.jar")) + ideClasspath)
            check(tasks.named<TestIdeUiTask>("testIdeUi").get().classpath.files.none { it.name == "second-ide.jar" })
            println("UI_CUSTOM_OK")
        """.trimIndent()

        build("help") { assertContains("UI_CUSTOM_OK", output) }
    }

    @Test
    fun `UI tasks create isolated source sets matching their names`() {
        buildFile write """
            intellijPlatformTesting.testIdeUi.register("smokeUi")
            tasks.register<TestIdeUiTask>("directUi")
            val defaultUi = sourceSets.getByName("testIdeUi")
            listOf("smokeUi", "directUi").forEach { name ->
                val sources = sourceSets.getByName(name)
                check(sources.java.srcDirs == setOf(file("src/" + name + "/java")))
                check(sources.resources.srcDirs == setOf(file("src/" + name + "/resources")))
                check(sources.allSource.srcDirs.contains(file("src/" + name + "/kotlin")))
                check(sources.implementationConfigurationName == name + "Implementation")
                check(sources.runtimeOnlyConfigurationName == name + "RuntimeOnly")
                val implementation = configurations.getByName(sources.implementationConfigurationName)
                check(configurations.getByName(defaultUi.implementationConfigurationName) !in implementation.hierarchy)
                check(configurations.getByName("testImplementation") !in implementation.hierarchy)
                val host = implementation.allDependencies.filterIsInstance<org.gradle.api.artifacts.ExternalModuleDependency>()
                check(host.count { it.name == "ide-starter-squashed" } == 1)
                check(host.count { it.name == "driver-client" } == 1)
                check(host.all { dependency -> dependency.excludeRules.none { it.group == "org.junit.vintage" } })
                val ui = tasks.named<TestIdeUiTask>(name).get()
                check(ui.testClassesDirs == sources.output.classesDirs)
                check(ui.testClassesDirs != defaultUi.output.classesDirs)
                check((ui.options as org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions).includeEngines.isEmpty())
                check((ui.options as org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions).excludeEngines.isEmpty())
                sources.runtimeClasspath = files(name + "-host.jar")
                check(ui.classpath.files.first() == file(name + "-host.jar"))
                check(ui.classpath.files.none { it in defaultUi.output.files })
            }
            println("UI_NAMED_SOURCES_OK")
        """.trimIndent()

        build("build", "--dry-run", "--offline") {
            assertContains("UI_NAMED_SOURCES_OK", output)
            assertFalse(output.contains(":smokeUi "))
            assertFalse(output.contains(":directUi "))
        }
    }

    @Test
    fun `Starter supplies JUnit Platform engines with explicit JUnit 4 and without engine filtering`() {
        val version = "262.12345.1"
        val junitVersion = "5.99.0-local"
        val repository = dir.resolve("host-repository")
        val junit = listOf(
            "org.junit.jupiter" to "junit-jupiter-api",
            "org.junit.jupiter" to "junit-jupiter-engine",
            "org.junit.platform" to "junit-platform-launcher",
            "org.junit.platform" to "junit-platform-engine",
            "org.junit.vintage" to "junit-vintage-engine",
        )
        fun publish(group: String, artifact: String, artifactVersion: String, dependencies: String = "") {
            val directory = repository.resolve("${group.replace('.', '/')}/$artifact/$artifactVersion").createDirectories()
            ZipOutputStream(directory.resolve("$artifact-$artifactVersion.jar").outputStream()).use { }
            directory.resolve("$artifact-$artifactVersion.pom") write """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>$group</groupId><artifactId>$artifact</artifactId><version>$artifactVersion</version>
                  <dependencies>$dependencies</dependencies>
                </project>
            """.trimIndent()
            // Lets the `closest` Starter version range resolve offline against this repository.
            directory.parent.resolve("maven-metadata.xml") write """
                <metadata>
                  <groupId>$group</groupId><artifactId>$artifact</artifactId>
                  <versioning><latest>$artifactVersion</latest><release>$artifactVersion</release><versions><version>$artifactVersion</version></versions></versioning>
                </metadata>
            """.trimIndent()
        }
        fun dependency(group: String, artifact: String, artifactVersion: String) =
            "<dependency><groupId>$group</groupId><artifactId>$artifact</artifactId><version>$artifactVersion</version></dependency>"

        junit.forEach { (group, artifact) ->
            publish(
                group, artifact, junitVersion,
                if (group == "org.junit.vintage") dependency("junit", "junit", "not-published") else "",
            )
        }
        publish("junit", "junit", "4.13.2")
        // A custom JUnit Platform engine added by the user must be discovered as-is.
        publish("example", "custom-engine", junitVersion, dependency("org.junit.platform", "junit-platform-engine", junitVersion))
        TestFrameworkType.Starter.coordinates.forEach { coordinate ->
            publish(
                coordinate.groupId, coordinate.artifactId, version,
                junit.joinToString("") { (group, artifact) -> dependency(group, artifact, junitVersion) } +
                    dependency("junit", "junit", "not-published"),
            )
        }
        buildFile write """
            repositories {
                exclusiveContent {
                    forRepository { maven { url = uri("host-repository") } }
                    filter {
                        includeGroupByRegex("com[.]jetbrains[.]intellij[.].*")
                        includeGroupByRegex("org[.]junit[.].*")
                        includeGroup("junit")
                        includeGroup("example")
                    }
                }
            }
            val integration = sourceSets.getByName("testIdeUi")
            dependencies {
                add(integration.runtimeOnlyConfigurationName, "example:custom-engine:$junitVersion")
                add(integration.runtimeOnlyConfigurationName, "junit:junit:4.13.2")
            }
            tasks.register("resolveUiHost") {
                val host = configurations.getByName(integration.runtimeClasspathConfigurationName).incoming.files
                inputs.files(host)
                doLast {
                    val names = host.files.map { it.name }
                    check("junit-jupiter-api-$junitVersion.jar" in names)
                    check("junit-jupiter-engine-$junitVersion.jar" in names)
                    check("junit-platform-launcher-$junitVersion.jar" in names)
                    check("junit-vintage-engine-$junitVersion.jar" in names)
                    check("junit-4.13.2.jar" in names)
                    check("custom-engine-$junitVersion.jar" in names)
                    check(names.none { it.startsWith("junit-not-published") })
                    println("UI_HOST_ENGINES_OK")
                }
            }
        """.trimIndent()

        build("resolveUiHost", "--offline") { assertContains("UI_HOST_ENGINES_OK", output) }
    }

    @Test
    fun `UI host receives archive and selected IDE version with configuration cache`() {
        emptyZipFile("custom.zip")
        dir.resolve("src/extraUi/java/ExtraHostTest.java") write """
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;

            public class ExtraHostTest {
                @Test
                void properties() throws Exception {
                    assertEquals(System.getProperty("expected.version"), System.getProperty("ide.test.version"));
                    assertTrue(System.getProperty("path.to.build.plugin").endsWith("custom.zip"));
                    assertThrows(ClassNotFoundException.class, () -> Class.forName("HostTest"));
                }
            }
        """.trimIndent()
        dir.resolve("src/testIdeUi/java/HostTest.java") write """
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;

            public class HostTest {
                @Test
                void properties() {
                    assertEquals(System.getProperty("expected.version"), System.getProperty("ide.test.version"));
                    assertTrue(System.getProperty("path.to.build.plugin").endsWith("custom.zip"));
                    assertThrows(ClassNotFoundException.class, () -> Class.forName("ExtraHostTest"));
                }
            }
        """.trimIndent()
        buildFile write """
            val hostJunit = configurations.create("hostJunit")
            dependencies {
                add(hostJunit.name, "org.junit.jupiter:junit-jupiter:5.11.4")
                add(hostJunit.name, "org.junit.platform:junit-platform-launcher:1.11.4")
            }
            intellijPlatformTesting.testIdeUi.register("extraUi")
            listOf("testIdeUi", "extraUi").forEach { name ->
                sourceSets.named(name) {
                    compileClasspath = hostJunit
                    runtimeClasspath = output + hostJunit
                }
                tasks.named<TestIdeUiTask>(name) {
                    archiveFile.set(layout.projectDirectory.file("custom.zip"))
                    val explicitVersion = providers.gradleProperty("uiVersion")
                    if (explicitVersion.isPresent) systemProperty("ide.test.version", explicitVersion.get())
                    systemProperty("expected.version", explicitVersion.getOrElse("2026.2.3"))
                }
            }
        """.trimIndent()

        build("testIdeUi", "extraUi")
        buildWithConfigurationCache("testIdeUi", "extraUi")
        buildWithConfigurationCache("testIdeUi", "extraUi") { assertContains("Reusing configuration cache.", output) }
        buildWithConfigurationCache("testIdeUi", "extraUi", projectProperties = mapOf("uiVersion" to "explicit-version"))
    }

    @Test
    fun `module plugin does not register UI tests requiring a plugin archive`() {
        buildFile overwrite """
            plugins { id("org.jetbrains.intellij.platform.module") }
            check("testIdeUi" !in tasks.names)
            check(extensions.getByType<org.gradle.api.plugins.JavaPluginExtension>().sourceSets.findByName("testIdeUi") == null)
            println("MODULE_OK")
        """.trimIndent()

        build("help") { assertContains("MODULE_OK", output) }
    }
}
