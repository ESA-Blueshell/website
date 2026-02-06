package net.blueshell.api.model.event

import net.blueshell.api.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EventPictureModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_join_columns() {
            val event = persistEvent()
            val file = persist(fileWithUploader(fileFactory.createImage()))

            val picture = eventPictureFactory.createBasic()
            picture.event = event
            picture.picture = file

            val found = persistAndReload(picture, EventPicture::class.java) { it.id }

            assertEquals(event.id, found.eventId)
            assertEquals(file.id, found.pictureId)
        }
    }
}
