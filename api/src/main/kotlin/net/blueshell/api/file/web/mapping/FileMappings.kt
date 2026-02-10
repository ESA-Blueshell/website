package net.blueshell.api.file.web.mapping

import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.web.dto.FileDTO
import tech.mappie.api.ObjectMappie

object FileToFileDTOMapper : ObjectMappie<File, FileDTO>()

object FileDTOToFileMapper : ObjectMappie<FileDTO, File>()

fun FileDTO.asEntity(): File {
    val mapped = FileDTOToFileMapper.map(this)
    id?.let { mapped.assignIdForRef(it) }
    return mapped
}

fun File.asDto(): FileDTO = FileToFileDTOMapper.map(this)
