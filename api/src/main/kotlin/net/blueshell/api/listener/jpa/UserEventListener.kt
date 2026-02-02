package net.blueshell.api.listener.jpa

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.common.enums.Role
import net.blueshell.api.common.event.job.SyncContactEvent
import net.blueshell.api.common.event.jpa.PostPersistEvent
import net.blueshell.api.common.event.jpa.PostUpdateEvent
import net.blueshell.api.model.User
import net.blueshell.api.model.committee.CommitteeMember
import net.blueshell.api.service.CommitteeMemberService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.function.Consumer

@Slf4j
@Component
@RequiredArgsConstructor
class UserEventListener {
    private val eventPublisher: ApplicationEventPublisher? = null
    private val committeeMembers: CommitteeMemberService? = null

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun postPersist(evt: PostPersistEvent<User>) {
        val u = evt.getSource()
        eventPublisher!!.publishEvent(SyncContactEvent(u.getId()))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: PostUpdateEvent<User>) {
        val u = evt.getSource()
        eventPublisher!!.publishEvent(SyncContactEvent(u.getId()))
        if (!u.hasRole(Role.MEMBER)) {
            u.getCommitteeMembers().forEach(Consumer { entity: CommitteeMember? -> committeeMembers!!.delete(entity) })
        }
    }
}
