package net.blueshell.api.architecture

import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import net.blueshell.api.shared.event.AfterCommitListener
import org.junit.jupiter.api.Test
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async

/**
 * A domain event reaches a plain `@EventListener` inside the publishing transaction's commit, where
 * a write that joins that transaction never commits. [AfterCommitListener] opens a new one, so a
 * listener of a domain event uses it, runs `@Async` off the committing thread, or is listed here
 * with why it does neither.
 */
class AfterCommitListenerArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {
    private companion object {
        val LISTED =
            mapOf(
                // Opens its transaction inside a try, so a failed activation email cannot fail
                // the registration that committed before it.
                "net.blueshell.api.auth.domain.RecoveryEventListener" to "onUserCreated",
            )
    }

    @Test
    fun `a domain event listener runs in a transaction of its own`(): Unit =
        arch("A domain event listener is an @AfterCommitListener") {
            methods()
                .that()
                .areAnnotatedWith(EventListener::class.java)
                .should(listenInATransactionOfTheirOwn())
                .because("a write joined to the committing transaction is lost")
        }

    private fun listenInATransactionOfTheirOwn() =
        object : ArchCondition<JavaMethod>("be @AfterCommitListener, @Async, or listed with why") {
            override fun check(
                method: JavaMethod,
                events: ConditionEvents,
            ) {
                // A listener naming its event class, such as ApplicationReadyEvent, hears the framework.
                val heard = method.getAnnotationOfType(EventListener::class.java)
                val framework = heard.classes.isNotEmpty() || heard.value.isNotEmpty()
                val async = method.isAnnotatedWith(Async::class.java)
                val listed = LISTED[method.owner.name] == method.name
                if (!framework && !async && !listed) {
                    events.add(SimpleConditionEvent.violated(method, "${method.fullName} is a plain @EventListener"))
                }
            }
        }
}
