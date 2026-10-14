package net.blueshell.api.mail

import net.blueshell.api.email.persistence.Email
import net.blueshell.api.email.persistence.EmailRepository
import net.blueshell.api.mail.domain.InboxIntake
import net.blueshell.api.mail.domain.ParsedInboxMessage
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
class InboxIT : UserTestSupport() {
    @Autowired
    private lateinit var intake: InboxIntake

    @Autowired
    private lateinit var emails: EmailRepository

    private fun received(
        id: String,
        from: String,
        thread: List<String> = emptyList(),
        to: String = "board@esa-blueshell.nl",
        automatic: Boolean = false,
    ) = ParsedInboxMessage(
        id,
        thread.firstOrNull(),
        thread,
        from,
        null,
        to,
        "Re: Your contribution",
        "Paid",
        null,
        Instant.now(),
        automatic,
    )

    @Test
    fun `a reply is matched to the email it answers and its sender, other mail keeps its address, and only the board reads it`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val sentId = "<${UUID.randomUUID()}@blueshell>"
        val sent =
            emails.save(
                Email(
                    recipientEmail = member.email,
                    subject = "Your contribution",
                    emailType = "email.contribution-reminder",
                    messageId = sentId,
                ),
            )
        val stamp = UUID.randomUUID()

        intake.take(received("<reply-$stamp>", member.email, listOf(sentId)))
        assertThat(intake.take(received("<reply-$stamp>", member.email, listOf(sentId)))).isNull()
        intake.take(received("<partner-$stamp>", "info@sponsor-$stamp.example", to = "partners@esa-blueshell.nl"))
        intake.take(received("<away-$stamp>", member.email, automatic = true))

        mvc
            .perform(get("/mail/inbox").param("search", member.email).with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[?(@.automatic == false)].answers.emailId").value(sent.id!!.toInt()))
            .andExpect(jsonPath("$.content[?(@.automatic == false)].senderUserId").value(member.id!!.toInt()))
            .andExpect(jsonPath("$.content[?(@.automatic == false)].state").value("NEW"))
        mvc
            .perform(get("/mail/inbox").param("search", "sponsor-$stamp").with(signedIn(board)))
            .andExpect(jsonPath("$.content[0].toAddress").value("partners@esa-blueshell.nl"))
            .andExpect(jsonPath("$.content[0].senderUserId").doesNotExist())
        mvc.perform(get("/mail/inbox/counts").with(signedIn(board))).andExpect(status().isOk)
        mvc.perform(get("/mail/inbox").with(signedIn(member))).andExpect(status().isForbidden)
    }
}
