package net.blueshell.api.file.api.mapper

import net.blueshell.api.factory.dto.FileDTOFactory
import net.blueshell.api.factory.model.FileFactory
import net.blueshell.api.file.api.mapper.FileMapper
import net.blueshell.api.file.domain.model.File
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class FileMapperIT @Autowired constructor(
    private val fileMapper: FileMapper,
    private val fileDTOFactory: FileDTOFactory,
    private val fileFactory: FileFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
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

    @Nested
    inner class FromDTO {
        @Test
        fun `persists mapped file fields`() {
            val dto = fileDTOFactory.createBasic()

            val mapped = fileMapper.fromDTO(dto)
            mapped.path = "/uploads/mapped-file"
            mapped.uploader = persistUser()

            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(File::class.java, saved.id!!)

            assertThat(reloaded.name).isEqualTo(dto.name)
            assertThat(reloaded.mediaType).isEqualTo(dto.mediaType)
            assertThat(reloaded.size).isEqualTo(dto.size)
            assertThat(reloaded.type).isEqualTo(dto.type)
        }
    }
}
