package net.blueshell.api.file.application.listener

import net.blueshell.api.file.application.event.FileDeleted
import net.blueshell.api.file.application.FileService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class FileEventListener(
    private val files: FileService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onDelete(evt: FileDeleted) {
        files.deleteFromStoragePath(evt.path)
    }
}
