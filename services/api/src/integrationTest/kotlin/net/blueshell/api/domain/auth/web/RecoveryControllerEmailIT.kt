package net.blueshell.api.domain.auth.web

import net.blueshell.api.domain.auth.application.factory.RecoveryTokenFactory
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

/**
 * Integration tests for recovery email scheduling.
 *
 * Tests the complete flow of the new email architecture (ADR-019, ADR-022):
 * 1. Controller receives request
 * 2. Service publishes domain event
 * 3. Listener schedules email job
 * 4. Email eventually gets sent (verified via job scheduling)
 *
 * This replaces the old RecoveryControllerIT that tested direct email sending.
 */
@SpringBootTest
class RecoveryControllerEmailIT : UserTestSupport() {

    @Autowired
    private lateinit var recoveryTokenFactory: RecoveryTokenFactory

    @BeforeEach
    fun clearOutbox() {
        emailTransportClient.reset()
    }

    @Nested
    inner class PasswordReset {

        @Test
        fun `schedules password reset email when requested by username`() {
            // Given: Existing user
            val user = createUserWithRole(Role.MEMBER)

            // When: Requesting password reset
            mvc.perform(post("/recovery/password/reset/{username}", user.username))
                .andExpect(status().isNoContent)

            // Then: Email job is scheduled
            val jobs = findJobsByType(EmailJobs.Recovery.type)
            assertThat(jobs)
                .describedAs("Should schedule recovery email job")
                .hasSize(1)

            val jobPayload = jobs.first().payload
            assertThat(jobPayload)
                .describedAs("Job should contain user ID and reset type")
                .contains("\"userId\":${user.id}")
                .contains("\"tokenPurpose\":\"PASSWORD_RESET\"")
        }

        @Test
        fun `returns success even for non-existent username (security)`() {
            // When: Requesting password reset for non-existent user
            mvc.perform(post("/recovery/password/reset/{username}", "nonexistent"))
                .andExpect(status().isNoContent)

            // Then: No jobs scheduled (but attacker doesn't know)
            val jobs = findJobsByType(EmailJobs.Recovery.type)
            assertThat(jobs)
                .describedAs("Should not schedule jobs for non-existent users")
                .isEmpty()
        }
    }

    @Nested
    inner class UserActivation {

        @Test
        fun `schedules user activation email when resending`() {
            // Given: Disabled user
            val user = createUserWithRole(Role.MEMBER, enabled = false)

            // When: Resending activation email
            mvc.perform(post("/recovery/user/activate/resend/{username}", user.username))
                .andExpect(status().isNoContent)

            // Then: Email job is scheduled
            val jobs = findJobsByType(EmailJobs.Recovery.type)
            assertThat(jobs)
                .describedAs("Should schedule recovery email job")
                .hasSize(1)

            val jobPayload = jobs.first().payload
            assertThat(jobPayload)
                .describedAs("Job should contain user ID and activation reset type")
                .contains("\"userId\":${user.id}")
                .contains("\"tokenPurpose\":\"USER_ACTIVATION\"")
        }

        @Test
        fun `does not send activation email for already enabled users`() {
            // Given: Enabled user
            val user = createUserWithRole(Role.MEMBER, enabled = true)

            // When: Attempting to resend activation (should succeed but not create job for enabled users)
            mvc.perform(post("/recovery/user/activate/resend/{username}", user.username))
                .andExpect(status().isNoContent) // Success response

            // Then: No email job scheduled (already enabled)
            val jobs = findJobsByType(EmailJobs.Recovery.type)
            assertThat(jobs)
                .describedAs("Should not schedule jobs for already enabled users")
                .isEmpty()
        }
    }

    @Nested
    inner class MemberActivation {

        @Test
        fun `schedules member activation email when board resends`() {
            // Given: Disabled user and board member
            val disabledUser = createUserWithRole(Role.MEMBER, enabled = false)
            val board = createUserWithRole(Role.BOARD)

            // And: Recovery token exists
            recoveryTokenFactory.issue(disabledUser, TokenPurpose.MEMBER_ACTIVATION, Duration.ofDays(7))

            // When: Board resends activation
            mvc.perform(
                post("/recovery/users/{userId}/resend/recovery", disabledUser.id)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)

            // Then: Email job is scheduled
            val jobs = findJobsByType(EmailJobs.Recovery.type)
            assertThat(jobs)
                .describedAs("Should schedule recovery email job")
                .hasSize(1)

            val jobPayload = jobs.first().payload
            assertThat(jobPayload)
                .describedAs("Job should contain user ID and member activation reset type")
                .contains("\"userId\":${disabledUser.id}")
                .contains("\"tokenPurpose\":\"MEMBER_ACTIVATION\"")
        }

        @Test
        fun `requires board role to resend member activation`() {
            // Given: Disabled user and regular user
            val disabledUser = createUserWithRole(Role.MEMBER, enabled = false)
            val regularUser = createUserWithRole(Role.MEMBER)

            // When: Regular user attempts to resend activation
            mvc.perform(
                post("/recovery/users/{userId}/resend/recovery", disabledUser.id)
                    .with(bearer(regularUser))
            )
                .andExpect(status().isForbidden)

            // Then: No email job scheduled
            val jobs = findJobsByType(EmailJobs.Recovery.type)
            assertThat(jobs)
                .describedAs("Should not allow non-board users to trigger member activation")
                .isEmpty()
        }
    }

    @Nested
    inner class EmailContentVerification {

        @Test
        fun `password reset email contains required information`() {
            // Given: User
            val user = createUserWithRole(Role.MEMBER)

            // When: Requesting password reset
            mvc.perform(post("/recovery/password/reset/{username}", user.username))
                .andExpect(status().isNoContent)

            // Then: Email job is scheduled with correct information
            val jobs = findJobsByType(EmailJobs.Recovery.type)
            assertThat(jobs)
                .describedAs("Should schedule recovery email job")
                .hasSize(1)

            val jobPayload = jobs.first().payload
            assertThat(jobPayload)
                .describedAs("Job payload should contain user ID")
                .contains("\"userId\":${user.id}")
                .contains("\"tokenPurpose\":\"PASSWORD_RESET\"")
                .contains("\"token\"")
        }
    }
}
