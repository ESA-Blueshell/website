//package net.blueshell.api.event;
//
//import lombok.extern.slf4j.Slf4j;
//import net.blueshell.api.common.enums.Role;
//import net.blueshell.api.common.event.PreInsertEvent;
//import net.blueshell.api.common.event.PostUpdateEvent;
//import net.blueshell.api.model.User;
//import net.blueshell.api.service.CommitteeMemberService;
//import net.blueshell.api.service.brevo.ContactService;
//import net.blueshell.api.service.brevo.EmailService;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.event.TransactionPhase;
//import org.springframework.transaction.event.TransactionalEventListener;
//import sendinblue.ApiException;
//
//@Slf4j
//@Component
//public class UserEventListener {
//
//    private final EmailService email;
//    private final ContactService contacts;
//    private final CommitteeMemberService committeeMembers;
//
//    public UserEventListener(EmailService email, ContactService contacts, CommitteeMemberService committeeMembers) {
//        this.email = email;
//        this.contacts = contacts;
//        this.committeeMembers = committeeMembers;
//    }
//
//    /**
//     * send e-mail only if the transaction COMMITTED successfully
//     */
//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    public void onUserCreated(PreInsertEvent<User> evt) throws ApiException {
//        User u = evt.getSource();
//        email.sendUserActivationEmail(u);
//        contacts.sync(u);
//    }
//
//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    public void onUserUpdated(PostUpdateEvent<User> evt) {
//        User u = evt.getSource();
//        if (!u.hasRole(Role.MEMBER)) {
//            log.warn("User {} has no role {}", u, Role.MEMBER);
//            log.warn("DELETING COMMITTERE MEMBERSHIPS");
//            u.getCommitteeMembers().forEach(committeeMembers::delete);
//        }
//    }
//
//    /**
//     * clean-up if the outer transaction rolls back
//     */
//    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
//    public void onFailure(PreInsertEvent<User> evt) {
//        User u = evt.getSource();
//    }
//}
