// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Source-only extraction, without distribution layout normalization or repository access. */
internal class SourceArchiveService(
    private val maxEntries: Int = 200_000,
    private val maxBytes: Long = 4L * 1024 * 1024 * 1024,
    private val lockTimeoutMillis: Long = 300_000,
) {
    fun extract(archive: Path, cache: Path, diagnostic: (String) -> Unit = {}): Path = extractRoots(archive, cache, diagnostic).first()

    fun extractRoots(archive: Path, cache: Path, diagnostic: (String) -> Unit = {}): List<Path> {
        require(Files.isRegularFile(archive, NOFOLLOW_LINKS))
        val fingerprint = digest(archive)
        val requested = cache.toAbsolutePath().normalize()
        Files.createDirectories(requested.parent)
        val base = requested.parent.toRealPath().resolve(requested.fileName).resolve("extracted/2")
        directories(base)
        val root = base.resolve(fingerprint)
        run {
            val lock = base.resolve("$fingerprint.lock")
            FileChannel.open(lock, CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
                acquireLock(channel).use {
                    Files.newDirectoryStream(base, "$fingerprint.extract-*").use { entries ->
                        entries.forEach { deleteTree(it) }
                    }
                    if (complete(root, fingerprint)) {
                        Files.readString(root.resolve("complete")).lineSequence().drop(2)
                            .filter { it.matches(Regex("sources.extraction.(duplicateEntries|caseCollisionRoots):[0-9]+")) }
                            .forEach(diagnostic)
                        return roots(root)
                    }
                    require(!Files.isSymbolicLink(root))
                    val temporary = Files.createTempDirectory(base, "$fingerprint.extract-")
                    try {
                        val notes = mutableListOf<String>()
                        unpack(archive, Files.createDirectory(temporary.resolve("files"))) { notes.add(it) }
                        require(digest(archive) == fingerprint)
                        Files.writeString(
                            temporary.resolve("complete"),
                            (listOf(fingerprint, rootsDigest(temporary)) + notes).joinToString("\n"),
                            CREATE_NEW,
                        )
                        if (Files.exists(root, NOFOLLOW_LINKS)) deleteTree(root)
                        Files.move(temporary, root, ATOMIC_MOVE)
                        notes.forEach(diagnostic)
                    } finally {
                        if (Files.exists(temporary, NOFOLLOW_LINKS)) deleteTree(temporary)
                    }
                    return roots(root)
                }
            }
        }
    }

    private fun acquireLock(channel: FileChannel): java.nio.channels.FileLock {
        val started = System.nanoTime()
        var logged = false
        while (true) {
            try {
                channel.tryLock()?.let { return it }
            } catch (_: OverlappingFileLockException) {
                // Another plugin classloader in this JVM can own the same process-wide lock.
            }
            check((System.nanoTime() - started) / 1_000_000 < lockTimeoutMillis) { "Source extraction lock wait timed out" }
            if (!logged) {
                java.util.logging.Logger.getLogger(SourceArchiveService::class.java.name)
                    .info("Waiting for source extraction cache writer")
                logged = true
            }
            Thread.sleep(25)
        }
    }

    private fun complete(root: Path, fingerprint: String): Boolean = runCatching {
        require(!Files.isSymbolicLink(root) && Files.isDirectory(root.resolve("files"), NOFOLLOW_LINKS))
        val marker = root.resolve("complete")
        require(Files.isRegularFile(marker, NOFOLLOW_LINKS) && Files.size(marker) in 129L..1024L)
        require(Files.readString(marker).lines().take(2) == listOf(fingerprint, rootsDigest(root)))
        true
    }.getOrDefault(false)

    private fun roots(root: Path): List<Path> = Files.newDirectoryStream(root).use { entries ->
        entries.filter { it.fileName.toString() != "complete" }.sortedBy { it.fileName.toString() }.also { paths ->
            require(paths.size in 1..8)
            require(paths.map { it.fileName.toString() } == paths.indices.map { if (it == 0) "files" else "files-$it" })
            require(paths.all { Files.isDirectory(it, NOFOLLOW_LINKS) && !Files.isSymbolicLink(it) })
        }
    }

    private fun rootsDigest(root: Path): String = roots(root).joinToString(":") { treeDigest(it) }.let { value ->
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun treeDigest(root: Path): String {
        val identity = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        Files.walk(root).use { paths ->
            val entries = paths.limit(maxEntries * 64L + 1).toList().sorted()
            require(entries.size <= maxEntries * 64L)
            entries.forEach {
                require(!Files.isSymbolicLink(it))
                val relative = root.relativize(it).toString().replace('\\', '/')
                identity.update(relative.toByteArray())
                identity.update(0.toByte())
                if (Files.isRegularFile(it, NOFOLLOW_LINKS)) {
                    bytes += Files.size(it)
                    require(bytes <= maxBytes)
                    identity.update(digest(it).toByteArray())
                } else require(Files.isDirectory(it, NOFOLLOW_LINKS))
                identity.update(0.toByte())
            }
        }
        return identity.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unpack(archive: Path, root: Path, diagnostic: (String) -> Unit) {
        validateCentralDirectory(archive)
        ZipFile(archive.toFile()).use { zip ->
            val entries = zip.entries().asSequence().take(maxEntries + 1).toList()
            require(entries.size <= maxEntries)
            val names = sortedMapOf<String, Boolean>()
            entries.forEach { entry ->
                val name = entry.name.removeSuffix("/")
                require(name.isNotEmpty() && name.length <= 4096 && '\\' !in name && ':' !in name)
                val parts = name.split('/')
                require(parts.size <= 64 && parts.none { part ->
                    part.isEmpty() || part in listOf(".", "..") || part.endsWith('.') || part.endsWith(' ') ||
                        part.any { it.code < 32 || it in "<>\"|?*" } || part.substringBefore('.').uppercase(Locale.ROOT) in reserved
                })
                parts.indices.forEach { index ->
                    val prefix = parts.take(index + 1).joinToString("/")
                    val directory = index < parts.lastIndex || entry.isDirectory
                    require(names.putIfAbsent(prefix, directory).let { it == null || it == directory })
                }
            }
            val partitions = mutableListOf<MutableMap<String, String>>()
            val assignments = mutableMapOf<String, Int>()
            names.forEach { (name, _) ->
                val parts = name.split('/')
                val prefixes = parts.indices.map { parts.take(it + 1).joinToString("/") }
                val keys = prefixes.map { Normalizer.normalize(it, Normalizer.Form.NFC).lowercase(Locale.ROOT) }
                val partition = partitions.indexOfFirst { paths ->
                    keys.indices.all { paths[keys[it]].let { previous -> previous == null || previous == prefixes[it] } }
                }.let {
                    if (it >= 0) it else {
                        require(partitions.size < 8) { "sources.extraction.pathCollision" }
                        partitions.add(mutableMapOf())
                        partitions.lastIndex
                    }
                }
                keys.indices.forEach { partitions[partition][keys[it]] = prefixes[it] }
                assignments[name] = partition
            }
            if (partitions.isEmpty()) partitions.add(mutableMapOf())
            val destinations = partitions.indices.map { if (it == 0) root else Files.createDirectory(root.resolveSibling("files-$it")) }
            if (partitions.size > 1) diagnostic("sources.extraction.caseCollisionRoots:${partitions.size}")
            val explicit = mutableMapOf<String, Boolean>()
            var duplicates = 0
            var expanded = 0L
            val buffer = ByteArray(8192)
            ZipInputStream(Files.newInputStream(archive).buffered()).use { stream ->
                entries.forEach { entry ->
                    val local = requireNotNull(stream.nextEntry)
                    require(local.name == entry.name && local.isDirectory == entry.isDirectory)
                    val name = entry.name.removeSuffix("/")
                    val previous = explicit.putIfAbsent(name, entry.isDirectory)
                    require(previous == null || previous == entry.isDirectory)
                    val duplicate = previous != null
                    if (duplicate) duplicates++
                    val partitionRoot = destinations[assignments.getValue(name)]
                    val destination = partitionRoot.resolve(name).normalize()
                    require(destination.startsWith(partitionRoot))
                    if (entry.isDirectory) Files.createDirectories(destination) else Files.createDirectories(destination.parent)
                    run {
                        val crc = CRC32()
                        var bytes = 0L
                        (if (duplicate || entry.isDirectory) java.io.OutputStream.nullOutputStream() else Files.newOutputStream(
                            destination,
                            CREATE_NEW,
                            WRITE,
                        )).use { output ->
                            val input = stream
                            while (true) {
                                val size = input.read(buffer)
                                if (size < 0) break
                                expanded += size
                                bytes += size
                                require(expanded <= maxBytes)
                                crc.update(buffer, 0, size)
                                output.write(buffer, 0, size)
                            }
                        }
                        require(bytes == entry.size && crc.value == entry.crc)
                    }
                    stream.closeEntry()
                }
                require(stream.nextEntry == null)
            }
            if (duplicates > 0) diagnostic("sources.extraction.duplicateEntries:$duplicates")
        }
    }

    // java.util.zip hides Unix file types. Reject links and special files before writing entries.
    private fun validateCentralDirectory(archive: Path) {
        RandomAccessFile(archive.toFile(), "r").use { file ->
            require(file.length() >= 22)
            val tail = ByteArray(minOf(file.length(), 65557).toInt())
            file.seek(file.length() - tail.size)
            file.readFully(tail)
            fun u16(bytes: ByteArray, offset: Int) =
                (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)

            fun u32(bytes: ByteArray, offset: Int) =
                u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)

            fun u64(bytes: ByteArray, offset: Int): Long {
                val high = u32(bytes, offset + 4)
                require(high <= Int.MAX_VALUE)
                return u32(bytes, offset) or (high shl 32)
            }

            val end = (tail.size - 22 downTo 0).firstOrNull {
                u32(tail, it) == 0x06054b50L && it + 22 + u16(tail, it + 20) == tail.size
            } ?: error("Invalid ZIP")
            val disk = u16(tail, end + 4)
            val centralDisk = u16(tail, end + 6)
            var count = u16(tail, end + 10).toLong()
            require(count == u16(tail, end + 8).toLong())
            var offset = u32(tail, end + 16)
            var size = u32(tail, end + 12)
            var directoryEnd = file.length() - tail.size + end
            val hasLocator = if (directoryEnd >= 20) {
                file.seek(directoryEnd - 20)
                u32(ByteArray(4).also(file::readFully), 0) == 0x07064b50L
            } else false
            if (hasLocator || count == 65535L || offset == 0xffffffffL || size == 0xffffffffL || disk == 65535 || centralDisk == 65535) {
                require(disk in listOf(0, 65535) && centralDisk in listOf(0, 65535))
                require(directoryEnd >= 20)
                file.seek(directoryEnd - 20)
                val locator = ByteArray(20).also(file::readFully)
                require(u32(locator, 0) == 0x07064b50L && u32(locator, 4) == 0L && u32(locator, 16) == 1L)
                val zip64Offset = u64(locator, 8)
                require(zip64Offset <= directoryEnd - 76)
                file.seek(zip64Offset)
                val record = ByteArray(56).also(file::readFully)
                require(u32(record, 0) == 0x06064b50L && u64(record, 4) == 44L)
                require(u32(record, 16) == 0L && u32(record, 20) == 0L)
                require(count == 65535L || count == u64(record, 32))
                require(offset == 0xffffffffL || offset == u64(record, 48))
                require(size == 0xffffffffL || size == u64(record, 40))
                count = u64(record, 32)
                require(count == u64(record, 24) && zip64Offset + 76 == directoryEnd)
                offset = u64(record, 48)
                size = u64(record, 40)
                directoryEnd = zip64Offset
            } else require(disk == 0 && centralDisk == 0)
            require(count <= maxEntries && offset <= directoryEnd && size <= directoryEnd - offset)
            val limit = offset + size
            require(limit == directoryEnd)
            file.seek(offset)
            repeat(count.toInt()) {
                val header = ByteArray(46)
                file.readFully(header)
                require(u32(header, 0) == 0x02014b50L && u16(header, 34) == 0)
                val mode = u16(header, 40) and 0xf000
                require(mode == 0 || mode == 0x8000 || mode == 0x4000)
                require(u16(header, 8) and 1 == 0)
                file.seek(file.filePointer + u16(header, 28) + u16(header, 30) + u16(header, 32))
                require(file.filePointer <= limit)
            }
            require(file.filePointer == limit)
        }
    }

    private fun directories(path: Path) {
        path.parent?.let { directories(it) }
        if (Files.exists(path, NOFOLLOW_LINKS)) require(Files.isDirectory(path, NOFOLLOW_LINKS))
        else runCatching { Files.createDirectory(path) }.getOrElse { require(Files.isDirectory(path, NOFOLLOW_LINKS)) }
    }

    private fun deleteTree(path: Path) = Files.walk(path).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
    }

    private fun digest(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val reserved = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }
    }
}
