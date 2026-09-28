package net.blueshell.api.auth.domain

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.user.api.UserCreated
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ActivationEmailDispatcherTest {
    @Test
    fun `sends a new account its activation link on behalf of whoever made it`() {
        val board = Actor.user(3, Role.BOARD)
        val jobs: JobQueue = mock()
        val activation: UserActivationService =
            mock { on { issueActivationForNewUser(7, true) } doReturn RecoveryDispatch(7, "sel.ver", TokenPurpose.USER_ACTIVATION) }

        ActivationEmailDispatcher(jobs, activation).dispatchFor(UserCreated(7, createdByBoard = true, actor = board))

        verify(jobs).runAsync(
            EmailJobs.Recovery,
            EmailJobs.RecoveryPayload(7, "sel.ver", TokenPurpose.USER_ACTIVATION),
            JobTrigger.SITE_ACTION,
            board,
        )
    }
}
