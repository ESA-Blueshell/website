package net.blueshell.api.domain.file.application.event

data class FileDeleted(
    val fileId: Long,
    val path: String
)
