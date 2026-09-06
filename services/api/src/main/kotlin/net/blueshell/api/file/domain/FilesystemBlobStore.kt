package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobNotStored
import net.blueshell.api.file.api.BlobStore
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * The only place in the application that knows the bytes are on a disk.
 *
 * Every `Paths.get` and `Files.*` behind [BlobStore] lives here, so swapping the volume for an
 * object store is one class rather than a search.
 *
 * A key is resolved against [root] and normalised, and one that lands outside [root] holds
 * nothing. Every key the running system writes is a kind's directory and a file name, so the
 * check refuses only what nothing legitimately produces — and it is the whole reason a row
 * whose path was tampered with cannot make this read an arbitrary file off the host.
 *
 * The root is created on the first write rather than at startup, so a store nobody writes to —
 * the shipped assets — does not conjure an empty directory beside the running application.
 */
class FilesystemBlobStore(location: String) : BlobStore {

    private val root: Path = Paths.get(location).normalize().toAbsolutePath()

    override fun exists(key: String): Boolean = Files.exists(resolve(key))

    override fun sizeOf(key: String): Long? =
        runCatching { Files.size(resolve(key)) }.getOrNull()

    override fun open(key: String): InputStream {
        val path = resolve(key)
        if (!Files.exists(path)) throw BlobNotStored(key)
        return Files.newInputStream(path)
    }

    /**
     * Written beside its destination and moved into place, so a reader never sees half a file
     * at an address that promises the bytes there can never change. The scratch copy is on the
     * same volume, which is what makes the move atomic rather than a copy.
     */
    override fun put(key: String, content: InputStream): Long {
        val destination = resolve(key)
        Files.createDirectories(destination.parent)
        if (Files.exists(destination)) {
            content.close()
            return Files.size(destination)
        }

        val scratch = Files.createTempFile(destination.parent, "blob-", ".tmp")
        try {
            content.use { input ->
                Files.newOutputStream(scratch).use(input::transferTo)
            }
            try {
                Files.move(scratch, destination, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: FileAlreadyExistsException) {
                Files.deleteIfExists(scratch)
            }
        } finally {
            Files.deleteIfExists(scratch)
        }
        return Files.size(destination)
    }

    override fun delete(key: String) {
        val path = resolve(key)
        try {
            Files.deleteIfExists(path)
        } catch (e: IOException) {
            log.error("Failed to delete file {}", path, e)
        }
    }

    private fun resolve(key: String): Path {
        val resolved = root.resolve(key).normalize()
        if (!resolved.startsWith(root)) throw BlobNotStored(key)
        return resolved
    }

    private companion object {
        val log = LoggerFactory.getLogger(FilesystemBlobStore::class.java)
    }
}
