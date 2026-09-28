package net.blueshell.api.auth.domain

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.api.RoleChanges
import net.blueshell.api.user.api.UserJobs
import net.blueshell.api.user.persistence.RoleChange
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper

class RoleChangeEmailJobTest {
    private val changes = mock<RoleChanges>()
    private val emails = mock<EmailSenderService>()
    private val mapper = JsonMapper.builder().findAndAddModules().build()
    private val job = RoleChangeEmailJob(mapper, changes, emails, "https://site")

    private fun person(
        id: Long,
        email: String,
    ) = User(username = "u$id", email = email, password = "h", initials = "U", firstName = "U", lastName = "$id").also { it.id = id }

    @Test
    fun `tells the person whose roles changed, reading the change through the user module`() {
        val change =
            RoleChange(person(7, "person@example.com"), person(1, "admin@example.com"), setOf(Role.MEMBER), setOf(Role.MEMBER, Role.BOARD))
        whenever(changes.find(42)).thenReturn(change)

        job.handle(mapper.writeValueAsString(UserJobs.RoleChangePayload(42)), 5, forced = false)

        val content = argumentCaptor<EmailContent>()
        verify(emails).send(content.capture(), eq(UserJobs.RoleChange.type), anyOrNull())
        assertThat(content.firstValue.recipientEmail).isEqualTo("person@example.com")
    }
}
