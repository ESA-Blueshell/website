package net.blueshell.api.factory.model.event

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.event.model.EventPicture
import org.junit.jupiter.api.Test

class EventPictureFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable event picture`() {
        val event = persistEvent()
        val pictureFile = fileWithUploader(fileFactory.createImage())

        val picture = eventPictureFactory.createBasic()
        picture.event = event
        picture.picture = persist(pictureFile)

        val saved = persist(picture)
        assertPersisted(EventPicture::class.java, saved.id)
    }
}
