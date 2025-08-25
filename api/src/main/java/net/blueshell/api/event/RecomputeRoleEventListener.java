//package net.blueshell.api.event;
//
//import lombok.extern.slf4j.Slf4j;
//import net.blueshell.api.common.enums.Role;
//import net.blueshell.api.common.event.*;
//import net.blueshell.api.model.CommitteeMember;
//import net.blueshell.api.service.UserService;
//import org.springframework.context.event.EventListener;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.event.TransactionPhase;
//import org.springframework.transaction.event.TransactionalEventListener;
//
//@Slf4j
//@Component
//public class RecomputeRoleEventListener {
//
//    private final UserService users;
//
//    public RecomputeRoleEventListener(UserService users) {
//        this.users = users;
//    }
//
//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    public void recomputeRole(RecomputeRolesEvent evt) {
//        log.debug("recomputeRole {}", evt);
//        users.synchronizeRole(evt.getRole());
//    }
//}