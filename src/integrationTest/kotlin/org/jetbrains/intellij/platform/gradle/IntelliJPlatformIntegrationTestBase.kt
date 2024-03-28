// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.*
import kotlin.test.BeforeTest

@OptIn(ExperimentalPathApi::class)
open class IntelliJPlatformIntegrationTestBase(
    private val resourceName: String? = null,
) : IntelliJPlatformTestBase() {

    @BeforeTest
    override fun setup() {
        super.setup()

        if (resourceName != null) {
            use(resourceName)
        }
    }

    protected fun use(resourceName: String) {
        val resourcePath = Path("src", "integrationTest", "resources", resourceName)

        if (resourcePath.notExists()) {
            throw IllegalArgumentException("Integration tests resource '$resourceName' not found in: $resourcePath")
        }

        resourcePath.copyToRecursively(
            target = dir,
            followLinks = true,
            overwrite = true,
        )
//        Files.walk(resourcePath)
//            .forEach {
//                val destinationFile = dir.resolve(resourcePath.relativize(it))
//                it.copyTo(destinationFile, true)
//            }
    }
//    /**
//     * Shorthand for the setup method.
//     */
//    fun File.init(block: Path.() -> Unit) = toPath().run(block)
//
//    /**
//     * Path to the integration tests single project,
//     * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/plugin-xml-patching/`.
//     */
//    val Path.projectDirectory
//        get() = toAbsolutePath().parent
//
//    /**
//     * Integration test name extracted from the project directory,
//     * e.g., `plugin-xml-patching`.
//     */
//    val Path.projectName
//        get() = projectDirectory.fileName.toString()
//
//    /**
//     * Path to the integration tests root directory,
//     * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/`.
//     */
//    val Path.testsRootDirectory
//        get() = projectDirectory.parent
//
//    /**
//     * Path to the IntelliJ Platform Gradle Plugin root directory,
//     * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/`.
//     */
//    val Path.rootDirectory
//        get() = testsRootDirectory.parent

    /**
     * Path to the build directory of the integration tests single project,
     * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/plugin-xml-patching/build/`.
     */
    val buildDirectory
        get() = dir.resolve("build")
            .also {
                assert(it.exists()) { "Build directory does not exist: $it" }
            }

//    /**
//     * Path to the Gradle Wrapper – uses first argument provided to the script or falls back to the project instance,
//     * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/gradlew`.
//     */
////    val Path.gradleWrapper
////        get() = args.firstOrNull() ?: rootDirectory.resolve("gradlew")
//
    /**
     * Path to the patched `plugin.xml` file located within the build directory of the integration tests single project,
     * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/plugin-xml-patching/build/patchedPluginXmlFiles/plugin.xml`.
     */
    val patchedPluginXml
        get() = buildDirectory.resolve("patchedPluginXmlFiles/plugin.xml") // TODO: fix location
            .also {
                assert(it.exists()) { "plugin.xml file does not exist: $it" }
            }

//    /**
//     * Path to the generated plugin ZIP archive located in build/distributions directory,
//     * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/plugin-xml-patching/build/distributions/plugin-xml-patching-1.0.0.zip`.
//     */
//    val Path.pluginArchive
//        get() = buildDirectory
//            .resolve("distributions/$projectName-1.0.0.zip")

    /**
     * Path to the generated plugin JAR archive located in build/libs directory,
     * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/plugin-xml-patching/build/libs/plugin-xml-patching-1.0.0.jar`.
     */
    val pluginJar
        get() = buildDirectory.resolve("libs/test-1.0.0.jar")
            .also {
                assert(it.exists()) { "Plugin jar file does not exist: $it" }
            }

    /**
     * Path to the Gradle cache directory., e.g., `/Users/hsz/.gradle/caches/modules-2/files-2.1`
     */
    val gradleCacheDirectory
        get() = gradleHome.resolve("caches/modules-2/files-2.1")
            .also {
                assert(it.exists()) { "Gradle cache directory does not exist: $it" }
            }

    /**
     * Path to the IDE plugins cache directory., e.g., `/Users/hsz/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/unzipped.com.jetbrains.plugins`.
     */
    @Deprecated("Obsolete in 2.0")
    val pluginsCacheDirectory
        get() = gradleCacheDirectory.resolve("com.jetbrains.intellij.idea/unzipped.com.jetbrains.plugins")
            .also {
                assert(it.exists()) { "IDE Plugins cache directory does not exist: $it" }
            }

    infix fun String.containsText(string: String) {
        assert(contains(string)) { "expect '$this' contains '$string'" }
    }

    infix fun String.notContainsText(string: String) {
        assert(!contains(string)) { "expect '$this' does not contain '$string'" }
    }

    infix fun Path.containsText(string: String) {
        readText().containsText(string)
    }

    infix fun String.matchesRegex(regex: String) {
        matchesRegex(regex.toRegex())
    }

    infix fun String.matchesRegex(regex: Regex) {
        assert(regex.containsMatchIn(this)) { "expect '$this' matches '$regex'" }
    }

    infix fun Path.containsFile(path: String) {
        assert(resolve(path).exists()) { "expect '$this' contains file '$path'" }
    }

    infix fun Path.notContainsFile(path: String) {
        assert(resolve(path).notExists()) { "expect '$this' not contains file '$path'" }
    }

    infix fun Path.containsFileInArchive(path: String) {
        val fs = FileSystems.newFileSystem(this, null as ClassLoader?)
        assert(fs.getPath(path).exists()) { "expect archive '$this' contains file '$path'" }
    }

    infix fun Path.readEntry(path: String) = ZipFile(pathString).use { zip ->
        val entry = zip.getEntry(path)
        zip.getInputStream(entry).bufferedReader().use { it.readText() }
    }

//    class TeeOutputStream(vararg targets: OutputStream) : OutputStream() {
//
//        private val targets = targets.toList()
//
//        override fun write(b: Int) = targets.forEach { it.write(b) }
//
//        override fun flush() = targets.forEach(OutputStream::flush)
//
//        override fun close() = targets.forEach(OutputStream::close)
//    }
}
