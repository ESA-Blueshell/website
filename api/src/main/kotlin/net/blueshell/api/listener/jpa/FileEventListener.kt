package net.blueshell.api.listener.jpa

import lombok.RequiredArgsConstructor
import net.blueshell.api.common.event.jpa.PostRemoveEvent
import net.blueshell.api.model.File
import net.blueshell.api.service.FileService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
@RequiredArgsConstructor
class FileEventListener {
    private val files: FileService? = null

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onDelete(evt: PostRemoveEvent<File>) {
        val f = evt.getSource()
        files!!.deleteFromStorage(f)
    }
}
