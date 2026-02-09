package net.blueshell.api.file.web.mapper

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.file.web.dto.FileDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.file.persistence.File
import org.springframework.stereotype.Component

@Konverter
interface FileKonverter {
    fun toDTO(file: File): FileDTO

    @Konvert(
        mappings = [
            Mapping(target = "mediaType", ignore = true),
            Mapping(target = "size", ignore = true),
            Mapping(target = "type", ignore = true),
        ]
    )
    fun fromDTO(dto: FileDTO): File
}

@Component
class FileMapper : BaseMapper<File, FileDTO>() {
    private val konverter = konverter<FileKonverter>()

    override fun fromDTO(dto: FileDTO): File {
        val file = konverter.fromDTO(dto)
        dto.mediaType?.let { file.mediaType = it }
        dto.size?.let { file.size = it }
        dto.type?.let { file.type = it }
        dto.id?.let { file.assignIdForRef(it) }
        return file
    }

    override fun toDTO(entity: File): FileDTO = konverter.toDTO(entity)
}

fun File.asDTO(mapper: FileMapper): FileDTO = mapper.toDTO(this)

fun FileDTO.asEntity(mapper: FileMapper): File = mapper.fromDTO(this)
