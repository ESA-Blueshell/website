package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class EventJobsListenerUnitTest {
    private val jobs: JobQueue = mock()

    @Test
    fun `sends a guest their sign-up email with the access link, and nobody else`() {
        val guest: EventSignUp =
            mock {
                on { id } doReturn 11
                on { this.guest } doReturn mock()
            }
        val signUps: EventSignUpService = mock { on { findById(11) } doReturn guest }
        val actor = Actor.user(4, Role.MEMBER)

        EventJobsListener(jobs, signUps).onPersist(EventSignUpCreated(11, "access", actor))
        EventJobsListener(jobs, signUps).onPersist(EventSignUpCreated(11, null, actor))

        verify(jobs).runAsync(EventJobs.EventSignup, EventJobs.EventSignupPayload(11, "access"), JobTrigger.SITE_ACTION, actor)
    }

    @Test
    fun `sends nothing for a sign-up that belongs to an account`() {
        val member: EventSignUp = mock { on { id } doReturn 12 }
        val signUps: EventSignUpService = mock { on { findById(12) } doReturn member }

        EventJobsListener(jobs, signUps).onPersist(EventSignUpCreated(12, "access"))

        verifyNoInteractions(jobs)
    }
}
