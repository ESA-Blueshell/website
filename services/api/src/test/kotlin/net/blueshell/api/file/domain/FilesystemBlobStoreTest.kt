package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class FilesystemBlobStoreTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `a stored file can be read by every user, so the nightly backup reads it too`() {
        assumeTrue("posix" in root.fileSystem.supportedFileAttributeViews())

        FilesystemBlobStore(root.toString()).put("event-banners/a.webp", "bytes".byteInputStream())

        val stored = root.resolve("event-banners/a.webp")
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(stored))).isEqualTo("rw-r--r--")
        assertThat(Files.readString(stored)).isEqualTo("bytes")
    }
}
