package net.blueshell.api.factory.model

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
        assertPersisted(net.blueshell.api.model.event.EventPicture::class.java, saved.id)
    }
}
