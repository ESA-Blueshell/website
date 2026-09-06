package net.blueshell.api.factory.file.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.domain.StoredFileNames
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.user.persistence.User
import org.springframework.stereotype.Component

/**
 * A stored file, written where the upload flow writes one.
 *
 * The bytes go through the store under a key of the kind's directory and a name, which is the
 * shape every key the running system writes has. It used to write into `/tmp` and put the
 * absolute path in the row — a shape nothing but this factory ever produced, and one that only
 * read back because the store resolved a whole path to itself.
 */
@Component
class FileFactory(
    private val persistence: FactoryPersistenceSupport,
    private val blobs: BlobStore,
) {
    fun build(
        uploader: User,
        name: String = "banner.png",
        mediaType: String = "image/png",
        type: FileType = FileType.EVENT_BANNER
    ): File {
        val key = StoredFileNames.keyOf(type.directory, "${System.nanoTime()}-$name")
        blobs.put(key, CONTENT.byteInputStream())
        return File(
            name = name,
            path = key,
            uploader = uploader,
            mediaType = mediaType,
            size = 1024,
            type = type,
        )
    }

    fun create(
        uploader: User,
        name: String = "banner.png",
        mediaType: String = "image/png",
        type: FileType = FileType.EVENT_BANNER
    ): File {
        return persistence.persist(build(uploader, name, mediaType, type))
    }

    private companion object {
        /** Deliberately not a picture: a fixture that measured would change what it stands for. */
        const val CONTENT = "test-file"
    }
}
