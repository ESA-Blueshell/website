package net.blueshell.api.domain.file.web.mapping

import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.web.dto.FileDTO
import tech.mappie.api.ObjectMappie

object FileToFileDTOMapper : ObjectMappie<File, FileDTO>()

fun FileDTO.asEntity(file: File = File()): File {
    file.name = name!!
    file.mediaType = mediaType!!
    file.size = size
    file.type = type!!
    version?.let { file.version = it }
    return file
}

fun File.asDto(): FileDTO = FileToFileDTOMapper.map(this)
