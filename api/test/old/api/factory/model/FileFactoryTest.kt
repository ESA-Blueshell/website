package net.blueshell.api.factory.model

import net.blueshell.api.domain.file.persistence.File
import org.junit.jupiter.api.Test

class FileFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable file`() {
        val file = fileWithUploader(fileFactory.createBasic())
        val saved = persist(file)
        assertPersisted(File::class.java, saved.id)
    }
}
