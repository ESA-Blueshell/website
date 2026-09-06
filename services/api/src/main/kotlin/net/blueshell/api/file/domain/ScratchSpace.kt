package net.blueshell.api.file.domain

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Somewhere to put bytes that are on their way somewhere else.
 *
 * Deliberately not the blob store. The store keeps what the site serves and would follow the
 * site to an object store; this exists only because the converter is a subprocess that will
 * take a file and nothing else, so bytes have to touch a disk on the way through whatever the
 * store is made of. Uploads are staged here too, since an upload is hashed and possibly
 * converted before its address is known and neither can be done to a stream that is gone.
 *
 * Cut on the same volume as the uploads, which is the one this deployment is willing to fill.
 */
@Component
class ScratchSpace(@Value($$"${storage.location}") location: String) {

    private val directory: Path = Paths.get(location)

    /** [content] on a disk, closed once it is there. */
    fun hold(content: InputStream, suffix: String = ".tmp"): ScratchFile {
        val file = cut(suffix)
        try {
            content.use { input -> Files.newOutputStream(file.path).use(input::transferTo) }
        } catch (e: Throwable) {
            file.close()
            throw e
        }
        return file
    }

    /** An empty file for a converter to write into. */
    fun cut(suffix: String): ScratchFile {
        Files.createDirectories(directory)
        return ScratchFile(Files.createTempFile(directory, "scratch-", suffix))
    }
}

/**
 * A working copy of some bytes, deleted when it is closed.
 *
 * [path] is here for the converter, which takes filenames on a command line. Nothing that is
 * not the converter should read it — everything else is served by [open] and [size].
 */
class ScratchFile internal constructor(val path: Path) : AutoCloseable {

    fun open(): InputStream = Files.newInputStream(path)

    fun size(): Long = Files.size(path)

    override fun close() {
        Files.deleteIfExists(path)
    }
}
