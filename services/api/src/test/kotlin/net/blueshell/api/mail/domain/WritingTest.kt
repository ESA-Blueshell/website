package net.blueshell.api.mail.domain

import net.blueshell.api.cohort.api.CohortAudiences
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.email.api.SiteMarkdownEmails
import net.blueshell.api.mail.persistence.WrittenEmail
import net.blueshell.api.mail.persistence.WrittenEmailRepository
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class WritingTest {
    private val audiences: CohortAudiences = mock()
    private val users: UserService = mock()
    private val written: WrittenEmailRepository = mock()
    private val jobs: JobQueue = mock()
    private val writing = Writing(audiences, users, written, jobs, Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC))

    private val ann = Entities.user(id = 1, username = "ann")
    private val bea = Entities.user(id = 2, username = "bea")
    private val nobody = Entities.user(id = 3, username = "nobody").apply { email = "" }

    @Test
    fun `reaches everybody a cohort, role or person names once, and counts who has no address`() {
        whenever(audiences.membersOf("ACTIVE_MEMBERS:4")).thenReturn(setOf(1, 3))
        whenever(users.findIdsHolding(Role.BOARD)).thenReturn(setOf(2))
        whenever(users.findAllByIds(setOf(1L, 3L, 2L))).thenReturn(listOf(ann, nobody, bea))

        val reach =
            writing.reach(
                listOf(
                    Addressee(AddresseeKind.COHORT, "ACTIVE_MEMBERS:4"),
                    Addressee(AddresseeKind.ROLE, "BOARD"),
                    Addressee(AddresseeKind.ROLE, "NO_SUCH_ROLE"),
                    Addressee(AddresseeKind.PERSON, "1"),
                    Addressee(AddresseeKind.PERSON, "x"),
                ),
            )

        assertThat(reach).isEqualTo(Reach(setOf(1, 2), 1))
    }

    @Test
    fun `keeps what was written once and queues one copy per person, and refuses an empty email or nobody`() {
        whenever(users.findAllByIds(setOf(1L, 2L))).thenReturn(listOf(ann, bea))
        whenever(written.save(any<WrittenEmail>())).thenAnswer { (it.arguments[0] as WrittenEmail).also { email -> email.id = 7 } }

        assertThat(
            writing.send(listOf(Addressee(AddresseeKind.PERSON, "1"), Addressee(AddresseeKind.PERSON, "2")), " Hi ", "Body", "", 9),
        ).isEqualTo(2)
        verify(jobs).runAsync(eq(MailJobs.Written), eq(MailJobs.WrittenPayload(7, 1)), eq(JobTrigger.SITE_ACTION), eq(null))
        verify(jobs).runAsync(eq(MailJobs.Written), eq(MailJobs.WrittenPayload(7, 2)), eq(JobTrigger.SITE_ACTION), eq(null))
        assertThat(writing.test("Hi", "Body", "board@b.nl", 9)).isEqualTo(1)
        verify(written, times(2)).save(any<WrittenEmail>())

        assertThatThrownBy { writing.test(" ", "Body", null, 9) }.isInstanceOf(SubjectMissing::class.java)
        assertThatThrownBy { writing.test("Hi", " ", null, 9) }.isInstanceOf(MessageMissing::class.java)
        whenever(users.findAllByIds(emptySet())).thenReturn(emptyList())
        assertThatThrownBy { writing.send(emptyList(), "Hi", "Body", null, 9) }.isInstanceOf(NobodyToWrite::class.java)
    }

    @Test
    fun `a copy goes to the person's address now, rendered from the site's markdown, with its reply address`() {
        val emails: EmailSenderService = mock()
        val siteMarkdown: SiteMarkdownEmails = mock()
        val job = WrittenEmailJob(JsonMapper(), emails, written, users, siteMarkdown)
        val email = WrittenEmail("Hi", "**Body**", "board@b.nl", 9, Instant.EPOCH, 1).also { it.id = 7 }
        whenever(written.findById(7)).thenReturn(Optional.of(email))
        whenever(written.findById(8)).thenReturn(Optional.empty())
        whenever(users.findById(1)).thenReturn(ann)
        whenever(users.findById(3)).thenReturn(nobody)
        whenever(siteMarkdown.forEmail("**Body**")).thenReturn("<b>Body</b>")

        assertThat(job.composeQueued("""{"writtenEmailId":7,"userId":1}"""))
            .isEqualTo(EmailContent(ann.email, ann.fullName, "Hi", "<b>Body</b>", replyToOverride = "board@b.nl"))
        assertThat(job.composeQueued("""{"writtenEmailId":7,"userId":3}""")).isNull()
        assertThatThrownBy { job.composeQueued("""{"writtenEmailId":8,"userId":1}""") }.isInstanceOf(NonRetryableJobException::class.java)
    }
}
