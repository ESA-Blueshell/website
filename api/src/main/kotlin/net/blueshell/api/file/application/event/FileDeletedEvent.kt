package net.blueshell.api.file.application.event

data class FileDeletedEvent(
    val fileId: Long,
    val path: String
)
