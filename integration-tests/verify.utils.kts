import java.nio.file.Files
import java.nio.file.Path

args.exitIf({ size < 2 }) { "Not enought arguments were not provided: '${joinToString()}'. Use: ./verify.main.kts <project dir path> <logs path>" }
val (workingDirArg, logsArg) = args

val workingDirPath = Path.of("").resolve(workingDirArg)
    .exitIf(Files::notExists) { "Working dir does not exist: ${toAbsolutePath()}" }
val logsPath = Path.of("").resolve(logsArg)
    .exitIf(Files::notExists) { "Logs file does not exist: ${toAbsolutePath()}" }

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

val logs by lazy {
    logsPath.let(Files::readString)
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
