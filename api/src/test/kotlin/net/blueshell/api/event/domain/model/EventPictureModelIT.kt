package net.blueshell.api.event.persistence

import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EventPictureModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists event relation when setting entity`() {
            val event = persistEvent()
            val file: File = persist(fileWithUploader(fileFactory.createImage()))

            val picture = eventPictureFactory.createBasic()
            picture.event = event
            picture.picture = file

            val found = persistAndReload(picture, EventPicture::class.java) { it.id }

            assertEquals(event.id, found.eventId)
            assertEquals(event.id, found.event.id)
        }

        @Test
        fun `persists event relation when setting id`() {
            val event = persistEvent()
            val file: File = persist(fileWithUploader(fileFactory.createImage()))

            val picture = eventPictureFactory.createBasic()
            picture.eventId = event.id!!
            picture.picture = file

            val found = persistAndReload(picture, EventPicture::class.java) { it.id }

            assertEquals(event.id, found.eventId)
            assertEquals(event.id, found.event.id)
        }

        @Test
        fun `persists picture relation when setting entity`() {
            val event = persistEvent()
            val file: File = persist(fileWithUploader(fileFactory.createImage()))

            val picture = eventPictureFactory.createBasic()
            picture.event = event
            picture.picture = file

            val found = persistAndReload(picture, EventPicture::class.java) { it.id }

            assertEquals(file.id, found.pictureId)
            assertEquals(file.id, found.picture.id)
        }

        @Test
        fun `persists picture relation when setting id`() {
            val event = persistEvent()
            val file: File = persist(fileWithUploader(fileFactory.createImage()))

            val picture = eventPictureFactory.createBasic()
            picture.event = event
            picture.pictureId = file.id!!

            val found = persistAndReload(picture, EventPicture::class.java) { it.id }

            assertEquals(file.id, found.pictureId)
            assertEquals(file.id, found.picture.id)
        }
    }
}
