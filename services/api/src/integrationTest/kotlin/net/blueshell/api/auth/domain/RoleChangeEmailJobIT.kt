package net.blueshell.api.auth.domain

import net.blueshell.api.platform.integration.mock.InMemoryEmailClient
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.testsupport.runJob
import net.blueshell.api.user.persistence.RoleChange
import net.blueshell.api.user.persistence.RoleChangeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * The notification a role change queues, delivered: what the person is told, and the outbox row
 * that makes a failure visible and retryable.
 */
@SpringBootTest
class RoleChangeEmailJobIT : UserTestSupport() {
    @Autowired
    private lateinit var job: RoleChangeEmailJob

    @Autowired
    private lateinit var roleChanges: RoleChangeRepository

    @Autowired
    private lateinit var emailClient: InMemoryEmailClient

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun record(
        before: Set<Role>,
        after: Set<Role>,
    ): RoleChange {
        val actor = createUserWithRole(Role.ADMIN)
        val subject = createUserWithRole(Role.MEMBER)
        return roleChanges.save(
            RoleChange(
                subject = subject,
                actor = actor,
                rolesBefore = before,
                rolesAfter = after,
                changedAt = Instant.now(),
            ),
        )
    }

    private fun run(change: RoleChange) = job.runJob(objectMapper.writeValueAsString(mapOf("roleChangeId" to change.id)))

    @Test
    fun `somebody made an admin is told where to look`() {
        val change = record(setOf(Role.MEMBER), setOf(Role.MEMBER, Role.ADMIN))

        run(change)

        val sent = emailClient.sentEmails.single()
        assertThat(sent.toEmail).isEqualTo(change.subject.email)
        assertThat(sent.htmlContent).contains("administrator access")
    }

    @Test
    fun `somebody who lost board is told a page disappearing is not a fault`() {
        val change = record(setOf(Role.MEMBER, Role.BOARD), setOf(Role.MEMBER))

        run(change)

        val sent = emailClient.sentEmails.single()
        assertThat(sent.htmlContent).contains("no longer hold board access")
    }

    @Test
    fun `the note the admin left is theirs, and stays out of the email`() {
        val change = record(setOf(Role.MEMBER), setOf(Role.MEMBER, Role.BOARD))
        change.note = "Stepping in for the treasurer"
        roleChanges.save(change)

        run(change)

        assertThat(emailClient.sentEmails.single().htmlContent).doesNotContain("Stepping in")
    }
}
