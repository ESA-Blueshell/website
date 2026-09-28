package net.blueshell.api.shared.event

import org.springframework.context.event.EventListener
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Listens to an event [AfterCommitEventPublisher] hands out once the change commits, in a
 * transaction of its own. The listener runs inside the finished transaction's commit, so a write
 * that joined it would never commit.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@EventListener
@Transactional(propagation = Propagation.REQUIRES_NEW)
annotation class AfterCommitListener
