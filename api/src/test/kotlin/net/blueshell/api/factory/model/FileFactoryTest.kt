package net.blueshell.api.factory.model

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.feature.file.model.File
import org.junit.jupiter.api.Test
import kotlin.jvm.java

class FileFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable file`() {
        val file = fileWithUploader(fileFactory.createBasic())
        val saved = persist(file)
        assertPersisted(File::class.java, saved.id)
    }
}
