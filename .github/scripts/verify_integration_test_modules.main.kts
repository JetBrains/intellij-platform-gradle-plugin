#!/usr/bin/env kotlin

/**
 * This script is used to list all modules available for integration tests.
 */

@file:DependsOn("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.0")

import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

val rootPath: Path = System.getenv("GITHUB_WORKSPACE")?.let(Path::of)
    ?: Path.of("").toAbsolutePath().parent.parent

val availableModules: List<String> = rootPath
    .resolve("integration-tests")
    .let { Files.list(it) }
    .filter { Files.isDirectory(it) }
    .map { it.fileName.toString() }
    .filter { !it.startsWith(".") }
    .sorted()
    .toList()

val usedModules: List<String> = rootPath
    .resolve(".github/workflows/reusable-integrationTests.yml")
    .let { Files.lines(it) }
    .dropWhile { it.trim() != "# INTEGRATION TESTS" }
    .filter { it.trim().startsWith("- name: ") }
    .map { it.trim().removePrefix("- name: ") }
    .sorted()
    .toList()

println("availableModules = $availableModules")
println("usedModules = $usedModules")

if (availableModules != usedModules) {
    throw Exception("Integration Test modules do not match")
}
