package net.blueshell.api.file.web.dto

import net.blueshell.api.factory.dto.FileDTOFactory
import net.blueshell.api.file.application.FileService
import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.web.mapping.asEntity
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class FileDtoIT @Autowired constructor(
    private val fileDTOFactory: FileDTOFactory,
    private val fileService: FileService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists mapped file fields`() {
            val dto = fileDTOFactory.createBasic()

            val mapped = dto.asEntity()
            mapped.path = "/uploads/mapped-file"
            mapped.uploader = persistUser()

            val saved = fileService.create(mapped)
            flushAndClear()

            val reloaded = reload(File::class.java, saved.id!!)

            assertThat(reloaded.name).isEqualTo(dto.name)
            assertThat(reloaded.mediaType).isEqualTo(dto.mediaType)
            assertThat(reloaded.size).isEqualTo(dto.size)
            assertThat(reloaded.type).isEqualTo(dto.type)
        }
    }
}
