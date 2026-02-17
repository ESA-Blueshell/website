package net.blueshell.api.domain.file.web.mapping.request

import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.domain.file.web.dto.FileDTO

fun FileDTO.asEntity(file: File): File {
    file.name = name!!
    file.mediaType = mediaType!!
    file.size = size
    file.type = type!!
    version?.let { file.version = it }
    return file
}
