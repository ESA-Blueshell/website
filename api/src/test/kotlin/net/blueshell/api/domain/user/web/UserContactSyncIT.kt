package net.blueshell.api.domain.user.web

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.mock.MockContactAdapter
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * End-to-end tests verifying that creating and updating users correctly
 * syncs contact data to the external system via the SyncContact → SyncContactToSystem job chain.
 */
@SpringBootTest
@TestPropertySource(properties = ["app.jobs.auto-dispatch=true"])
class UserContactSyncIT : UserTestSupport() {

    @Autowired
    private lateinit var mockContactAdapter: MockContactAdapter

    @Autowired
    private lateinit var jobs: TrackedJobDispatcher

    @Autowired
    private lateinit var users: UserService

    @BeforeEach
    fun clearMocks() {
        mockContactAdapter.clear()
    }

    @Test
    fun `creating user syncs contact data to external system`() {
        val member = createUserWithRole(Role.MEMBER)

        // Enqueue SyncContact (mimicking what UserEventListener does on UserCreated)
        enqueueInTransaction {
            jobs.enqueue(
                ContactJobs.SyncContact,
                ContactJobs.SyncContactPayload(member.id!!)
            )
        }

        awaitJobSuccess(ContactJobs.SyncContact.type)
        awaitJobSuccess(ContactJobs.SyncContactToSystem.type)

        val contacts = mockContactAdapter.getAllContacts()
        assertThat(contacts).hasSize(1)

        val contact = contacts.values.single()
        assertThat(contact.email).isEqualTo(member.email)
        assertThat(contact.firstName).isEqualTo(member.firstName)
        assertThat(contact.isMember).isTrue()
    }

    @Test
    fun `updating user syncs updated contact data`() {
        val member = createUserWithRole(Role.MEMBER)

        // First sync
        enqueueInTransaction {
            jobs.enqueue(
                ContactJobs.SyncContact,
                ContactJobs.SyncContactPayload(member.id!!)
            )
        }
        awaitJobSuccess(ContactJobs.SyncContact.type)
        awaitJobSuccess(ContactJobs.SyncContactToSystem.type)

        val contactBefore = mockContactAdapter.getAllContacts().values.single()
        assertThat(contactBefore.firstName).isEqualTo("Test")

        // Update user name
        transactionTemplate.executeWithoutResult {
            val user = users.findById(member.id!!)
            user.firstName = "UpdatedName"
            users.update(user)
        }

        // Second sync
        enqueueInTransaction {
            jobs.enqueue(
                ContactJobs.SyncContact,
                ContactJobs.SyncContactPayload(member.id!!)
            )
        }

        awaitJobSuccess(ContactJobs.SyncContact.type, expectedCount = 2)
        awaitJobSuccess(ContactJobs.SyncContactToSystem.type, expectedCount = 2)

        val contactAfter = mockContactAdapter.getAllContacts().values.single()
        assertThat(contactAfter.firstName).isEqualTo("UpdatedName")
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun enqueueInTransaction(enqueue: () -> Unit) {
        transactionTemplate.executeWithoutResult {
            enqueue()
        }
    }

    private fun awaitJobSuccess(
        jobType: String,
        expectedCount: Int = 1,
        timeoutMs: Long = 5_000,
        pollMs: Long = 100
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val successCount = findJobsByType(jobType).count { it.status == JobExecutionStatus.SUCCESS }
            if (successCount >= expectedCount) return
            Thread.sleep(pollMs)
        }

        val executions = findJobsByType(jobType)
        val successCount = executions.count { it.status == JobExecutionStatus.SUCCESS }
        assertThat(successCount)
            .describedAs(
                "Expected $expectedCount successful $jobType jobs, but found $successCount. " +
                    "Statuses: ${executions.map { "${it.id}=${it.status}" }}"
            )
            .isGreaterThanOrEqualTo(expectedCount)
    }
}
