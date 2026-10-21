// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.sources

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import kotlin.io.path.*

/** Repository-independent discovery. Persistent entries contain archive-intrinsic facts only. */
internal class IntelliJPlatformSourceResolver(private val cache: Path) {
    fun discover(input: SourceDiscoveryInput): SourcePlan {
        val root = Path(input.root).toAbsolutePath().normalize()
        val selected = input.owners.filter { input.owner == null || it.id == input.owner || input.owner in it.aliases }
        val selectedArchives = selected.flatMap { it.archives }.toSet()
        val paths = input.binaryPaths.mapNotNull { path ->
            root.resolve(path).normalize().takeIf { it.startsWith(root) }?.let { root.relativize(it).invariantSeparatorsPathString }
        }.distinct().sorted().filter { input.owner == null || it in selectedArchives }
        val observations = linkedMapOf<String, ArchiveMetadata>()
        val binaries = paths.map { path ->
            val file = root.resolve(path)
            val exists = file.isRegularFile()
            val fingerprint = if (exists) runCatching { fingerprint(file) }.getOrNull() else null
            val metadata = when {
                !exists -> ArchiveMetadata(diagnostics = listOf("binary.missing"))
                fingerprint == null -> ArchiveMetadata(diagnostics = listOf("binary.unreadable"))
                else -> inspect(file, fingerprint)
            }
            observations[path] = metadata
            SourceBinary(
                "binary-${digest(path)}", path,
                input.owners.filter { path in it.archives }.map { it.ref }.sorted(),
                exists, fingerprint,
                if (metadata.diagnostics.isEmpty()) "inspected" else "partial", metadata.diagnostics, metadata.entries.map { it.entry }.distinct().sorted(),
            )
        }
        val binaryIds = binaries.map { it.id }
        val ownerRefs = binaries.flatMap { it.owners }.distinct().sorted()
        val sources = mutableListOf<SourceRecord>()
        if (binaries.isNotEmpty()) {
            val local = (listOf(root.resolve("src.zip"), root.resolve("sources.zip")) +
                root.resolve("lib/src").takeIf { it.isDirectory() }?.listDirectoryEntries().orEmpty())
                .filter { it.isRegularFile() && it.extension in listOf("jar", "zip") }.sortedBy { it.toString() }
            local.forEach { file ->
                val relative = root.relativize(file).invariantSeparatorsPathString
                sources += SourceRecord(
                    "local-${digest(relative)}", binaryIds, ownerRefs, "local",
                    evidence = listOf("SDK-local candidate; coverage is not verified.", relative),
                    availability = "available", archive = file.toAbsolutePath().safePathString,
                )
            }
            val candidates = platformCandidates(input.productCode, input.version, input.buildNumber)
            sources += SourceRecord(
                "platform-${digest(input.productCode + input.buildNumber)}", binaryIds, ownerRefs, "platformMapping",
                candidates = candidates,
                evidence = listOf("Product mapping identifies candidates, not class or proprietary-source coverage.", "rules:$RULE_SCHEMA"),
                availability = if (candidates.isEmpty()) "unsupported" else "notRequested",
                candidateProvenance = candidates.mapIndexed { index, coordinate ->
                    when {
                        index == 0 -> "exactCoordinate"
                        coordinate.version.endsWith("-SNAPSHOT") -> "snapshotFallback"
                        else -> "compatibleFallback"
                    }
                },
            )
        }
        val embedded = binaries.flatMap { binary -> observations.getValue(binary.path).entries.map { Triple(it.coordinate, binary, it.entry) } }
        embedded.groupBy { it.first }.toList().sortedBy { it.first.notation }.forEach { (coordinate, matches) ->
            sources += SourceRecord(
                "maven-${digest(coordinate.notation)}", matches.map { it.second.id }.distinct().sorted(),
                matches.flatMap { it.second.owners }.distinct().sorted(), "embeddedMaven", listOf(coordinate),
                provenance = "exactCoordinate", evidence = matches.map { "${it.second.path}!/${it.third}" }.distinct().sorted(),
            )
        }
        val selectedInput = input.copy(binaryPaths = paths, owners = input.owners.sortedBy { it.ref })
        val identity = "$RULE_SCHEMA:$SCANNER_SCHEMA:" + json.encodeToString(selectedInput) + binaries.joinToString { "${it.path}:${it.fingerprint}:${it.inspection}" }
        return SourcePlan(selectedInput, digest(identity), binaries, sources.sortedBy { it.id }, emptyList(), RULE_SCHEMA)
    }

    private fun inspect(file: Path, fingerprint: String): ArchiveMetadata = synchronized(cacheLock) {
        val entry = cache.resolve("metadata/$SCANNER_SCHEMA/$fingerprint.json")
        val cached = readMetadata(entry, fingerprint)
        if (cached != null) return@synchronized cached
        val scanned = scan(file).copy(fingerprint = fingerprint)
        // Never persist an observation under an identity that changed while the archive was being read.
        if (runCatching { fingerprint(file) == fingerprint }.getOrDefault(false)) {
            if (scanned.diagnostics.none { it.endsWith(".unreadable") }) runCatching { publishMetadata(entry, scanned) }
        } else return@synchronized ArchiveMetadata(diagnostics = listOf("binary.changed"))
        scanned
    }

    private fun readMetadata(entry: Path, fingerprint: String): ArchiveMetadata? = runCatching {
        require(entry.isRegularFile() && entry.fileSize() <= MAX_CACHE_BYTES)
        val bytes = entry.inputStream().use { it.readNBytes(MAX_CACHE_BYTES + 1) }
        require(bytes.size <= MAX_CACHE_BYTES)
        json.decodeFromString<ArchiveMetadata>(bytes.decodeToString()).also {
            require(it.schema == SCANNER_SCHEMA && it.fingerprint == fingerprint)
            require(it.entries.size <= MAX_METADATA_ENTRIES && it.entries.all { item -> valid(item.coordinate) && metadataName.matches(item.entry) })
            require(it.diagnostics.all { diagnostic -> diagnostic in diagnostics })
        }
    }.getOrNull()

    private fun publishMetadata(entry: Path, metadata: ArchiveMetadata) {
        entry.parent.createDirectories()
        FileChannel.open(entry.resolveSibling("${entry.fileName}.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                if (readMetadata(entry, metadata.fingerprint) != null) return
                val text = json.encodeToString(metadata)
                require(text.toByteArray().size <= MAX_CACHE_BYTES)
                val temporary = createTempFile(entry.parent, "metadata-", ".tmp")
                try {
                    temporary.writeText(text)
                    Files.move(temporary, entry, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } finally {
                    temporary.deleteIfExists()
                }
            }
        }
    }

    private fun scan(file: Path): ArchiveMetadata {
        val found = mutableListOf<MavenMetadata>()
        val problems = sortedSetOf<String>()
        try {
            ZipFile(file.toFile()).use { zip ->
                require(zip.size() <= MAX_ENTRIES)
                var count = 0
                var bytes = 0
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.name.startsWith("META-INF/maven/") || !entry.name.endsWith("/pom.properties")) continue
                    if (++count > MAX_METADATA_ENTRIES) { problems += "metadata.limit"; break }
                    if (!metadataName.matches(entry.name) || entry.isDirectory) { problems += "metadata.invalid"; continue }
                    val content = runCatching { zip.getInputStream(entry).use { it.readNBytes(MAX_METADATA_BYTES + 1) } }.getOrNull()
                    if (content == null) { problems += "metadata.unreadable"; continue }
                    bytes += content.size
                    if (bytes > MAX_TOTAL_BYTES) { problems += "metadata.limit"; break }
                    if (content.size > MAX_METADATA_BYTES) { problems += "metadata.limit"; continue }
                    val coordinate = runCatching {
                        val properties = Properties().apply { content.inputStream().use { load(it) } }
                        SourceCoordinate(properties.getProperty("groupId").orEmpty(), properties.getProperty("artifactId").orEmpty(), properties.getProperty("version").orEmpty())
                    }.getOrNull()
                    if (coordinate == null || !valid(coordinate)) { problems += "metadata.invalid"; continue }
                    found += MavenMetadata(entry.name, coordinate)
                }
            }
        } catch (_: Exception) {
            problems += "archive.unreadable"
        }
        return ArchiveMetadata(entries = found.distinct().sortedWith(compareBy({ it.coordinate.notation }, { it.entry })), diagnostics = problems.toList())
    }

    companion object {
        const val SCANNER_SCHEMA = "1"
        const val RULE_SCHEMA = "2"
        private const val MAX_ENTRIES = 100_000
        private const val MAX_METADATA_ENTRIES = 256
        private const val MAX_METADATA_BYTES = 65_536
        private const val MAX_TOTAL_BYTES = 1_048_576
        private const val MAX_CACHE_BYTES = 2_097_152
        private val cacheLock = Any()
        private val json = Json { encodeDefaults = true }
        private val token = Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,255}")
        private val metadataName = Regex("META-INF/maven/[A-Za-z0-9_.-]{1,256}/[A-Za-z0-9_.-]{1,256}/pom\\.properties")
        private val diagnostics = setOf("binary.missing", "binary.unreadable", "binary.changed", "metadata.limit", "metadata.invalid", "metadata.unreadable", "archive.unreadable")

        private fun valid(coordinate: SourceCoordinate) =
            listOf(coordinate.group, coordinate.artifact, coordinate.version).all { token.matches(it) && it != "." && it != ".." } &&
                coordinate.version.uppercase() !in setOf("LATEST", "RELEASE") && !coordinate.version.endsWith("-SNAPSHOT", ignoreCase = true) && coordinate.classifier == "sources"

        fun fingerprint(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            path.inputStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val length = stream.read(buffer)
                    if (length < 0) break
                    digest.update(buffer, 0, length)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Mirrors DevKit source coordinates, not the binary product Maven mapping. */
        fun platformCandidates(product: String, version: String, build: String): List<SourceCoordinate> {
            val actual = build.removePrefix("$product-").substringBefore('+').ifEmpty { version.substringBefore('+') }
            val first = actual.substringBefore('.').toIntOrNull() ?: return emptyList()
            val major = if (first >= 2000) (first - 2000) * 10 + (actual.split('.').getOrNull(1)?.toIntOrNull() ?: 0) else first
            val group = if (product in listOf("PY", "PC")) "com.jetbrains.intellij.pycharm" else "com.jetbrains.intellij.idea"
            val artifact = when {
                product in listOf("PY", "PC") -> "pycharmPC"
                major >= 253 -> "idea"
                product == "IU" && major >= 242 -> "ideaIU"
                else -> "ideaIC"
            }
            return listOf(actual, "[$major,$actual]!!$actual", "$major-SNAPSHOT").distinct().map { SourceCoordinate(group, artifact, it) }
        }
    }
}

@Serializable
private data class MavenMetadata(val entry: String, val coordinate: SourceCoordinate)

@Serializable
private data class ArchiveMetadata(
    val schema: String = IntelliJPlatformSourceResolver.SCANNER_SCHEMA,
    val fingerprint: String = "",
    val entries: List<MavenMetadata> = emptyList(),
    val diagnostics: List<String> = emptyList(),
)