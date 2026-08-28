package net.blueshell.api.file.web

import net.blueshell.api.file.persistence.File

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
