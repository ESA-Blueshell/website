package net.blueshell.api.factory.file.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.FileType
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

@Component
class FileFactory(
    private val persistence: FactoryPersistenceSupport
) {
    fun build(
        uploader: User,
        name: String = "banner.png",
        mediaType: String = "image/png",
        type: FileType = FileType.EVENT_BANNER
    ): File {
        val path = Path.of("/tmp", "$name-${System.currentTimeMillis()}")
        Files.writeString(path, "test-file")
        return File(
            name = name,
            path = path.toString(),
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
}
