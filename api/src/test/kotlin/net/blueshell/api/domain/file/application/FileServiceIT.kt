package net.blueshell.api.domain.file.application

import net.blueshell.api.domain.file.application.event.FileDeleted
import net.blueshell.api.factory.model.FileFactory
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.testsupport.ServiceTestSupport
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.TestPropertySource
import java.nio.file.Files
import java.nio.file.Path

@TestPropertySource(properties = ["storage.location=/tmp/blueshell-test-storage"])
class FileServiceIT : ServiceTestSupport() {

    @Autowired
    private lateinit var files: FileService

    @Autowired
    private lateinit var fileFactory: FileFactory

    @Autowired
    private lateinit var userFactory: UserFactory

    @Value($$"${storage.location}")
    private lateinit var storageLocation: String

    @Nested
    inner class Delete {

        @Test
        fun `publishes file deleted event and removes storage file`() {
            val uploader = persist(userFactory.createBasic())
            val file = fileFactory.createWithCustomizations {
                it.uploader = uploader
                it.type = FileType.DOCUMENT
                it.mediaType = "text/plain"
                it.path = "uploads/test-file.txt"
            }

            val saved = files.create(file)
            val path = Path.of(storageLocation).resolve(saved.path)
            Files.createDirectories(path.parent)
            Files.writeString(path, "test")
            assertTrue(Files.exists(path))

            files.delete(saved)

            assertTrue(applicationEvents.stream(FileDeleted::class.java).anyMatch { it.fileId == saved.id })
            assertFalse(Files.exists(path))
        }
    }
}
