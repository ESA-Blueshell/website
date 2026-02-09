package net.blueshell.api.file.domain.model

import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.file.domain.model.File
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
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
            file.uploaderId = persist(userFactory.createBasic()).id!!

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

            assertEquals(uploader.id, found.uploaderId)
            assertEquals(uploader.id, found.uploader.id)
        }

        @Test
        fun `persists uploader relation when setting id`() {
            val file = fileFactory.createBasic()
            file.name = unique("file")
            file.path = "/uploads/${unique("file")}.txt"
            file.mediaType = "text/plain"
            file.type = FileType.DOCUMENT
            val uploader = persist(userFactory.createBasic())
            file.uploaderId = uploader.id!!

            val found = persistAndReload(file, File::class.java) { it.id }

            assertEquals(uploader.id, found.uploaderId)
            assertEquals(uploader.id, found.uploader.id)
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
            file.uploaderId = uploaderTwo.id!!

            val found = persistAndReload(file, File::class.java) { it.id }

            assertEquals(uploaderTwo.id, found.uploaderId)
            assertEquals(uploaderTwo.id, found.uploader.id)
        }
    }
}
