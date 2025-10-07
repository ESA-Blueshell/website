package net.blueshell.api.event;

import net.blueshell.api.common.event.PostRemoveEvent;
import net.blueshell.api.model.File;
import net.blueshell.api.service.FileService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FileEventListener {

    private final FileService files;

    public FileEventListener(FileService files) {
        this.files = files;
    }

//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void onDelete(PostRemoveEvent<File> evt) {
//        var f = evt.getSource();
//        files.deleteFromStorage(f);
//    }
}
