package net.blueshell.api.mapper

import net.blueshell.api.factory.model.FileFactory
import net.blueshell.api.model.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class FileMapperIT @Autowired constructor(
    private val fileMapper: FileMapper,
    private val fileFactory: FileFactory
) : MapperTestSupport() {
    @Test
    fun `maps persisted file`() {
        val file = fileWithUploader(fileFactory.createBasic())
        val saved = persist(file)
        flushAndClear()

        val reloaded = reload(File::class.java, saved.id!!)
        val dto = fileMapper.toDTO(reloaded)

        assertThat(dto.id).isEqualTo(reloaded.id)
        assertThat(dto.name).isEqualTo(reloaded.name)
        assertThat(dto.mediaType).isEqualTo(reloaded.mediaType)
        assertThat(dto.size).isEqualTo(reloaded.size)
        assertThat(dto.type).isEqualTo(reloaded.type)
    }
}
