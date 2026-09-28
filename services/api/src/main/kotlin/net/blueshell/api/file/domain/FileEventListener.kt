package net.blueshell.api.file.domain

import net.blueshell.api.file.api.FileService
import net.blueshell.api.shared.event.AfterCommitListener
import org.springframework.stereotype.Component

@Component
class FileEventListener(
    private val files: FileService,
) {
    @AfterCommitListener
    fun onDelete(evt: FileDeleted) {
        files.deleteFromStoragePath(evt.path)
    }
}
