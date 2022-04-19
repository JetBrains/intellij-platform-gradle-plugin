import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

val workingDirPath by lazy {
    Path.of("")
        .toAbsolutePath()
        .exitIf(Files::notExists) { "Working dir does not exist: ${toAbsolutePath()}" }
}

val (gradleArg) = args.toList() + listOf(null)
val gradle = gradleArg ?: workingDirPath.parent.parent.resolve("gradlew").toString()

val buildDirectory by lazy {
    workingDirPath
        .resolve("build")
        .exitIf(Files::notExists) { "build directory does not exist: ${toAbsolutePath()}" }
}

val patchedPluginXml: String by lazy {
    buildDirectory
        .resolve("patchedPluginXmlFiles/plugin.xml")
        .exitIf(Files::notExists) { "plugin.xml file does not exist: ${toAbsolutePath()}" }
        .let(Files::readString)
}

fun runGradleTask(task: String) =
    ProcessBuilder()
        .command(gradle, task, "--info")
        .directory(workingDirPath.toAbsolutePath().toFile())
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
    assert(contains(string))
}

infix fun String.matchesRegex(regex: String) {
    matchesRegex(regex.toRegex())
}

infix fun String.matchesRegex(regex: Regex) {
    assert(regex.containsMatchIn(this))
}

infix fun Path.containsFile(path: String) {
    assert(resolve(path).let(Files::exists))
}

infix fun Path.containsFileInArchive(path: String) {
    val fs = FileSystems.newFileSystem(this, null as ClassLoader?)
    assert(fs.getPath(path).let(Files::exists))
}

infix fun Path.readEntry(path: String) = ZipFile(toFile()).use { zip ->
    val entry = zip.getEntry(path)
    zip.getInputStream(entry).bufferedReader().use { it.readText() }
}


class TeeOutputStream(vararg targets: OutputStream) : OutputStream() {

    val targets = targets.toList()
    override fun write(b: Int) {
        targets.forEach { it.write(b) }
    }

    override fun flush() {
        targets.forEach(OutputStream::flush)
    }

    override fun close() {
        targets.forEach(OutputStream::close)
    }
}
