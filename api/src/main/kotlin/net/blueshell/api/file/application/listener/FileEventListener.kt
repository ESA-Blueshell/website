package net.blueshell.api.file.application.listener

import net.blueshell.api.shared.event.jpa.PostRemoveEvent
import net.blueshell.api.file.domain.model.File
import net.blueshell.api.file.application.FileService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class FileEventListener(
    private val files: FileService
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onDelete(evt: PostRemoveEvent<File>) {
        val f = evt.source
        files.deleteFromStorage(f)
    }
}
