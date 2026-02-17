package net.blueshell.api.domain.file.web.mapping.response

import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.domain.file.web.dto.FileDTO
import net.blueshell.api.domain.file.web.dto.FileResponse
import tech.mappie.api.ObjectMappie

object FileToFileDTOMapper : ObjectMappie<File, FileDTO>()

object FileToFileResponseMapper : ObjectMappie<File, FileResponse>()

fun File.asDto(): FileDTO = FileToFileDTOMapper.map(this)

fun File.asResponse(): FileResponse = FileToFileResponseMapper.map(this)
