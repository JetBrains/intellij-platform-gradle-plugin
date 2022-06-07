#!/usr/bin/env kotlin

/**
 * This script is used to list all modules available for integration tests.
 */

@file:DependsOn("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.20")

import java.nio.file.Files
import java.nio.file.Path

val rootPath: Path = System.getenv("GITHUB_WORKSPACE")?.let(Path::of)
    ?: Path.of("").toAbsolutePath().parent.parent

val modules: List<String> = rootPath
    .let { it.resolve("integration-tests") }
    .let { Files.list(it) }
    .filter { Files.isDirectory(it) }
    .map { it.fileName.toString() }
    .filter { !it.startsWith(".") }
    .toList()

println("[\"${modules.joinToString("\", \"")}\"]")
