package net.blueshell.api.declaration.domain

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.testsupport.runJob
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class DeclarationPaidEmailJobTest {
    private val mapper = JsonMapper()
    private val emails: EmailSenderService = mockk(relaxed = true)
    private val users: UserService = mockk()
    private val job = DeclarationPaidEmailJob(mapper, users, emails)
    private val paid = DeclarationJobs.DeclarationPaidPayload(43, 12, "Pizza and 2 more", 1500, "NL34")

    @Test
    fun `names the declaration and the account it went to, masked, and replies go to the board`() {
        every { users.findById(12) } returns Entities.user(12, firstName = "Ann", lastName = "Vos")
        val sent = mutableListOf<EmailContent>()
        every { emails.send(capture(sent), any(), any()) } returns Unit

        job.runJob(mapper.writeValueAsString(paid))

        val email = sent.single()
        assertThat(email.recipientEmail).isEqualTo(Entities.user(12).email)
        assertThat(email.subject).contains("paid")
        assertThat(email.replyToOverride).isEqualTo("board@blueshell.utwente.nl")
        assertThat(email.markdownContent).contains("**Pizza and 2 more** of €15.00", "NL•• … ••34")
        assertThat(job.jobType).isEqualTo("email.declaration-paid")
        assertThat(job.resendable).isTrue()
        assertThat(DeclarationJobs.DeclarationPaid.dedupKey(paid)).isNull()
    }
}
