import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Shorthand for the setup method.
 */
fun File.init(block: Path.() -> Unit) = toPath().run(block)

/**
 * Path to the integration tests single project,
 * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/plugin-xml-patching/`.
 */
val Path.projectDirectory
    get() = toAbsolutePath().parent

/**
 * Integration test name extracted from the project directory,
 * e.g., `plugin-xml-patching`.
 */
val Path.projectName
    get() = projectDirectory.fileName.toString()

/**
 * Path to the integration tests root directory,
 * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/`.
 */
val Path.testsRootDirectory
    get() = projectDirectory.parent

/**
 * Path to the Gradle IntelliJ Plugin root directory,
 * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/`.
 */
val Path.rootDirectory
    get() = testsRootDirectory.parent

/**
 * Path to the build directory of the integration tests single project,
 * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/plugin-xml-patching/build/`.
 */
val Path.buildDirectory
    get() = projectDirectory.resolve("build")
        .exitIf(Files::notExists) { "build directory does not exist: ${toAbsolutePath()}" }

/**
 * Path to the Gradle Wrapper – uses first argument provided to the script or falls back to the project instance,
 * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/gradlew`.
 */
val Path.gradleWrapper
    get() = args.firstOrNull() ?: rootDirectory.resolve("gradlew")

/**
 * Path to the patched `plugin.xml` file located within the build directory of the integration tests single project,
 * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/plugin-xml-patching/build/patchedPluginXmlFiles/plugin.xml`.
 */
val Path.patchedPluginXml
    get() = buildDirectory
        .resolve("patchedPluginXmlFiles/plugin.xml")
        .exitIf(Files::notExists) { "plugin.xml file does not exist: ${toAbsolutePath()}" }

/**
 * Path to the generated plugin ZIP achive located in build/distrubutions directory,
 * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/plugin-xml-patching/build/distributions/plugin-xml-patching-1.0.0.zip`.
 */
val Path.pluginArchive
    get() = buildDirectory
        .resolve("distributions/$projectName-1.0.0.zip")

/**
 * Path to the generated plugin JAR achive located in build/libs directory,
 * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/plugin-xml-patching/build/libs/plugin-xml-patching-1.0.0.jar`.
 */
val Path.pluginJar
    get() = buildDirectory
        .resolve("libs/$projectName-1.0.0.jar")
        .exitIf(Files::notExists) { "Plugin jar file does not exist: ${toAbsolutePath()}" }

/**
 * Path to the Gradle user home directory., e.g., `/Users/hsz/.gradle`.
 */
val Path.gradleUserHomeDirectory
    get() = Path.of(
        System.getProperty("gradle.user.home")
            ?: System.getenv("GRADLE_USER_HOME")
            ?: "${System.getProperty("user.home")}/.gradle"
    ).exitIf(Files::notExists) { "Gradle user home directory does not exist: ${toAbsolutePath()}" }

/**
 * Path to the Gradle cache directory.,
 * e.g., `/Users/hsz/.gradle/caches/modules-2/files-2.1`
 */
val Path.gradleCacheDirectory
    get() = gradleUserHomeDirectory
        .resolve("caches/modules-2/files-2.1")
        .exitIf(Files::notExists) { "Gradle cache directory does not exist: ${toAbsolutePath()}" }

/**
 * Path to the IDE plugins cache directory.,
 * e.g., `/Users/hsz/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/unzipped.com.jetbrains.plugins`.
 */
val Path.pluginsCacheDirectory
    get() = gradleCacheDirectory
        .resolve("com.jetbrains.intellij.idea/unzipped.com.jetbrains.plugins")
        .exitIf(Files::notExists) { "IDE Plugins cache directory does not exist: ${toAbsolutePath()}" }

/**
 * Runs the given Gradle task(s) within the current integration test.
 * Provides logs to STDOUT and as a returned value for further assertions.
 */
fun Path.runGradleTask(vararg tasks: String, projectProperties: Map<String, Any> = emptyMap()) =
    ProcessBuilder()
        .command(
            gradleWrapper.toString(),
            *projectProperties
                .run {
                    this + mapOf(
                        "platformVersion" to System.getenv("PLATFORM_VERSION"),
                    ).filterNot { it.value == null }
                }
                .map { "-P${it.key}=${it.value}" }.toTypedArray(),
            *tasks.map { ":$projectName:$it" }.toTypedArray(),
            "--info",
            "--stacktrace",
        )
        .apply { environment().put("INTEGRATION_TEST", projectName) }
        .directory(projectDirectory.toFile())
        .start()
        .run {
            val stdoutBuffer = ByteArrayOutputStream()
            val stderrBuffer = ByteArrayOutputStream()

            inputStream.copyTo(TeeOutputStream(stdoutBuffer, System.out))
            errorStream.copyTo(TeeOutputStream(stderrBuffer, System.err))

            stdoutBuffer.toString() + stderrBuffer.toString()
        }

fun <T> T.exitIf(block: T.() -> Boolean, message: T.() -> String = { "" }): T {
    if (block()) {
        println(message())
        System.exit(-1)
    }
    return this
}

infix fun String.containsText(string: String) {
    assert(contains(string)) { "expect '$this' contains '$string'" }
}

infix fun Path.containsText(string: String) {
    Files.readString(this).containsText(string)
}

infix fun String.matchesRegex(regex: String) {
    matchesRegex(regex.toRegex())
}

infix fun String.matchesRegex(regex: Regex) {
    assert(regex.containsMatchIn(this)) { "expect '$this' matches '$string'" }
}

infix fun Path.containsFile(path: String) {
    assert(resolve(path).let(Files::exists)) { "expect '$this' contains file '$path'" }
}

infix fun Path.containsFileInArchive(path: String) {
    val fs = FileSystems.newFileSystem(this, null as ClassLoader?)
    assert(fs.getPath(path).let(Files::exists)) { "expect '$this' contains file in archive '$path'" }
}

infix fun Path.readEntry(path: String) = ZipFile(toFile()).use { zip ->
    val entry = zip.getEntry(path)
    zip.getInputStream(entry).bufferedReader().use { it.readText() }
}

class TeeOutputStream(vararg targets: OutputStream) : OutputStream() {

    private val targets = targets.toList()

    override fun write(b: Int) = targets.forEach { it.write(b) }

    override fun flush() = targets.forEach(OutputStream::flush)

    override fun close() = targets.forEach(OutputStream::close)
}
