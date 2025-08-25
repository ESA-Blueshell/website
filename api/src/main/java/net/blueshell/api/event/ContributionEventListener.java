//package net.blueshell.api.event;
//
//import net.blueshell.api.common.event.PreInsertEvent;
//import net.blueshell.api.common.event.PostDeleteEvent;
//import net.blueshell.api.model.Contribution;
//import net.blueshell.api.service.brevo.ContactService;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.event.TransactionPhase;
//import org.springframework.transaction.event.TransactionalEventListener;
//import sendinblue.ApiException;
//
//@Component
//public class ContributionEventListener {
//
//    private final ContactService contacts;
//
//    public ContributionEventListener(ContactService contacts) {
//        this.contacts = contacts;
//    }
//
//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    public void onContributionCreated(PreInsertEvent<Contribution> evt) throws ApiException {
//        var c = evt.getSource();
//        contacts.addToList(c.getContributionPeriod(), c.getUser());
//    }
//
//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    public void onContributionDeleted(PostDeleteEvent<Contribution> evt) throws ApiException {
//        var c = evt.getSource();
//        contacts.removeFromList(c.getContributionPeriod(), c.getUser());
//    }
//}
