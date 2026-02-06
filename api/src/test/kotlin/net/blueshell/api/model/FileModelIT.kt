package net.blueshell.api.model

import net.blueshell.api.common.enums.FileType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FileModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_column_fields_and_uploader_relation() {
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
            assertEquals(file.uploaderId, found.uploaderId)
            assertEquals(file.uploader.id, found.uploader.id)
        }
    }
}
