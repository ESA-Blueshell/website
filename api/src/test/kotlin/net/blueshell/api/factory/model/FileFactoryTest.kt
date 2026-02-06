package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class FileFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable file`() {
        val file = fileWithUploader(fileFactory.createBasic())
        val saved = persist(file)
        assertPersisted(net.blueshell.api.model.File::class.java, saved.id)
    }
}
