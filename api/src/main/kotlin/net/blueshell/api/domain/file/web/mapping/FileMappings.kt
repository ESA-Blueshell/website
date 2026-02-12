package net.blueshell.api.domain.file.web.mapping

import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.domain.file.web.dto.FileDTO
import net.blueshell.api.domain.file.web.dto.FileResponse
import tech.mappie.api.ObjectMappie

object FileToFileDTOMapper : ObjectMappie<File, FileDTO>()

object FileToFileResponseMapper : ObjectMappie<File, FileResponse>()

fun FileDTO.asEntity(file: File = File()): File {
    file.name = name!!
    file.mediaType = mediaType!!
    file.size = size
    file.type = type!!
    version?.let { file.version = it }
    return file
}

fun File.asDto(): FileDTO = FileToFileDTOMapper.map(this)

fun File.asResponse(): FileResponse = FileToFileResponseMapper.map(this)
