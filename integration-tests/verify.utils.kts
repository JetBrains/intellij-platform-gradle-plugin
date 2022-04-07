import java.nio.file.Files
import java.nio.file.Path

val rootPath: Path by lazy {
    System.getenv("GITHUB_WORKSPACE")?.let(Path::of)
        ?: Path.of("").toAbsolutePath().parent.parent
}

val buildDirectory by lazy {
    Path.of("")
        .resolve("build")
        .throwIf({ RuntimeException("build directory does not exist: ${toAbsolutePath()}") }, Files::notExists)
}

val patchedPluginXml: String by lazy {
    buildDirectory
        .resolve("patchedPluginXmlFiles/plugin.xml")
        .throwIf({ RuntimeException("plugin.xml file does not exist: ${toAbsolutePath()}") }, Files::notExists)
        .let(Files::readString)
}

val buildOutput by lazy {
    args
        .throwIf("Input file was not provided", Array<String>::isEmpty)
        .let { Path.of(it.first()) }
        .throwIf({ RuntimeException("Input file does not exist: ${toAbsolutePath()}") }, Files::notExists)
        .let(Files::readString)
}


fun <T> T.throwIf(message: String, block: T.() -> Boolean) = throwIf({ RuntimeException(message) }, block)
fun <T> T.throwIf(exception: T.() -> Exception, block: T.() -> Boolean): T {
    if (block()) {
        throw exception()
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
