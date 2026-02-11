package net.blueshell.api.domain.file.persistence

import net.blueshell.api.domain.file.web.mapping.asDto
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FileModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val file = fileFactory.createBasic()
            file.name = unique("file")
            file.path = "/uploads/${unique("file")}.txt"
            file.mediaType = "text/plain"
            file.size = 2048
            file.type = FileType.DOCUMENT
            file.uploader = persist(userFactory.createBasic())

            val found = persistAndReload(file, File::class.java) { it.id }

            assertEquals(file.name, found.name)
            assertEquals(file.path, found.path)
            assertEquals(file.mediaType, found.mediaType)
            assertEquals(file.size, found.size)
            assertEquals(file.type, found.type)
        }

        @Test
        fun `persists uploader relation when setting entity`() {
            val file = fileFactory.createBasic()
            file.name = unique("file")
            file.path = "/uploads/${unique("file")}.txt"
            file.mediaType = "text/plain"
            file.type = FileType.DOCUMENT
            val uploader = persist(userFactory.createBasic())
            file.uploader = uploader

            val found = persistAndReload(file, File::class.java) { it.id }

            Assertions.assertEquals(uploader.id, found.uploaderId)
            Assertions.assertEquals(uploader.id, found.uploader.id)
        }

        @Test
        fun `persists uploader relation when setting id`() {
            val file = fileFactory.createBasic()
            file.name = unique("file")
            file.path = "/uploads/${unique("file")}.txt"
            file.mediaType = "text/plain"
            file.type = FileType.DOCUMENT
            val uploader = persist(userFactory.createBasic())
            file.uploader = uploader

            val found = persistAndReload(file, File::class.java) { it.id }

            Assertions.assertEquals(uploader.id, found.uploaderId)
            Assertions.assertEquals(uploader.id, found.uploader.id)
        }

        @Test
        fun `last setter wins when switching uploader id`() {
            val file = fileFactory.createBasic()
            file.name = unique("file")
            file.path = "/uploads/${unique("file")}.txt"
            file.mediaType = "text/plain"
            file.type = FileType.DOCUMENT
            val uploaderOne = persist(userFactory.createBasic())
            val uploaderTwo = persist(userFactory.createBasic())
            file.uploader = uploaderOne

            val found = persistAndReload(file, File::class.java) { it.id }

            Assertions.assertEquals(uploaderTwo.id, found.uploaderId)
            Assertions.assertEquals(uploaderTwo.id, found.uploader.id)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted file`() {
            val file = fileWithUploader(fileFactory.createBasic())
            val saved = persist(file)
            entityManager.flush()
            entityManager.clear()

            val reloaded = entityManager.find(File::class.java, saved.id)
            val dto = reloaded.asDto()

            assertEquals(reloaded.id, dto.id)
            assertEquals(reloaded.name, dto.name)
            assertEquals(reloaded.mediaType, dto.mediaType)
            assertEquals(reloaded.size, dto.size)
            assertEquals(reloaded.type, dto.type)
        }
    }
}