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

class DeclarationReturnedEmailJobTest {
    private val mapper = JsonMapper()
    private val emails: EmailSenderService = mockk(relaxed = true)
    private val users: UserService = mockk()
    private val job = DeclarationReturnedEmailJob(mapper, users, emails, "https://blueshell.test")
    private val sentBack =
        DeclarationJobs.DeclarationReturnedPayload(
            declarationId = 43,
            submitterId = 12,
            outcome = DeclarationJobs.Outcome.SENT_BACK,
            reason = "Line one\nLine two",
            title = "Pizza and 2 more",
            totalCents = 1500,
        )

    @Test
    fun `quotes the reason, names the declaration as the board saw it and says what the submitter can do`() {
        every { users.findById(12) } returns Entities.user(12, firstName = "Ann", lastName = "Vos")
        val sent = mutableListOf<EmailContent>()
        every { emails.send(capture(sent), any(), any()) } returns Unit

        job.runJob(mapper.writeValueAsString(sentBack))
        job.runJob(mapper.writeValueAsString(sentBack.copy(outcome = DeclarationJobs.Outcome.REJECTED, reason = "No")))

        val (back, rejected) = sent
        assertThat(back.recipientEmail).isEqualTo(Entities.user(12).email)
        assertThat(back.subject).contains("needs changes")
        assertThat(back.replyToOverride).isEqualTo("board@blueshell.utwente.nl")
        assertThat(back.markdownContent)
            .contains("**Pizza and 2 more** of €15.00", "> Line one\n> Line two", "https://blueshell.test/account/declarations/43")
        assertThat(rejected.subject).contains("rejected")
        assertThat(rejected.markdownContent).contains("You cannot submit this declaration again").doesNotContain("/account/declarations")
    }

    @Test
    fun `files the email under its own type, and may send it again`() {
        assertThat(job.jobType).isEqualTo("email.declaration-returned")
        assertThat(job.resendable).isTrue()
        assertThat(DeclarationJobs.DeclarationReturned.dedupKey(sentBack)).isNull()
    }
}
