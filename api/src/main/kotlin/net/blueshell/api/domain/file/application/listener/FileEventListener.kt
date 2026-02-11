package net.blueshell.api.domain.file.application.listener

import net.blueshell.api.domain.file.application.FileService
import net.blueshell.api.domain.file.application.event.FileDeleted
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
