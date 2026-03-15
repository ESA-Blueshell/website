package net.blueshell.api.domain.file.web.mapping.response

import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.domain.file.web.dto.response.FileResponse

fun File.asResponse(): FileResponse =
    FileResponse(
        id = this.id!!,
        name = this.name,
        mediaType = this.mediaType,
        size = this.size,
        type = this.type,
        path = this.path,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
