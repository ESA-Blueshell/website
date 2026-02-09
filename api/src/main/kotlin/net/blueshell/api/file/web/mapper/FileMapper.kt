package net.blueshell.api.file.web.mapper

import net.blueshell.api.file.web.dto.FileDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.file.persistence.File
import org.springframework.stereotype.Component

@Component
class FileMapper : BaseMapper<File, FileDTO>() {
    override fun fromDTO(dto: FileDTO): File {
        return File().also { file ->
            dto.id?.let { file.assignIdForRef(it) }
            dto.name?.let { file.name = it }
            dto.mediaType?.let { file.mediaType = it }
            dto.size?.let { file.size = it }
            dto.type?.let { file.type = it }
            dto.version?.let { file.version = it }
        }
    }

    override fun toDTO(file: File): FileDTO {
        return FileDTO(
            name = file.name,
            mediaType = file.mediaType,
            size = file.size,
            type = file.type
        ).also { dto ->
            dto.id = file.id
            dto.version = file.version
        }
    }
}

fun File.asDTO(mapper: FileMapper): FileDTO = mapper.toDTO(this)

fun FileDTO.asEntity(mapper: FileMapper): File = mapper.fromDTO(this)
