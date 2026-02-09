package net.blueshell.api.file.application.event

data class FileDeleted(
    val fileId: Long,
    val path: String
)
