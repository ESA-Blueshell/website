package net.blueshell.api.mail.web

import net.blueshell.api.cohort.api.Audience
import net.blueshell.api.cohort.api.CohortAudiences
import net.blueshell.api.mail.domain.Addressee
import net.blueshell.api.mail.domain.AddresseeKind
import net.blueshell.api.mail.domain.Reach
import net.blueshell.api.mail.domain.Writing
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException

class WritingControllerTest {
    private val writing: Writing = mock()
    private val audiences: CohortAudiences = mock()
    private val users: UserService = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val controller = WritingController(writing, audiences, users, currentUser, "board@b.nl")
    private val to = listOf(Addressee(AddresseeKind.PERSON, "1"))

    @Test
    fun `hands the board's writing through, as the writer`() {
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(id = 9, roles = emptySet(), addressId = null))
        whenever(users.findById(9)).thenReturn(Entities.user(id = 9, username = "writer"))
        whenever(audiences.all()).thenReturn(listOf(Audience("ACTIVE_MEMBERS:4", "Active members 2026-2027")))
        whenever(writing.reach(to)).thenReturn(Reach(setOf(1), 2))
        whenever(writing.send(to, "Hi", "Body", "board@b.nl", 9)).thenReturn(1)
        whenever(writing.test("Hi", "Body", null, 9)).thenReturn(1)

        assertThat(controller.findAudiences().single().label).isEqualTo("Active members 2026-2027")
        assertThat(controller.findReplyToOptions()).first().isEqualTo("board@b.nl")
        assertThat(controller.findReach(ReachRequest(to))).isEqualTo(ReachResponse(1, 2))
        assertThat(controller.sendWrittenEmail(WriteEmailRequest(to, "Hi", "Body", "board@b.nl")).sent).isEqualTo(1)
        assertThat(controller.sendTestEmail(WriteEmailRequest(subject = "Hi", message = "Body")).sent).isEqualTo(1)
        assertThat(ReachRequest().to).isEmpty()

        whenever(currentUser.currentUser()).thenReturn(null)
        assertThatThrownBy { controller.sendTestEmail(WriteEmailRequest()) }.isInstanceOf(ResponseStatusException::class.java)
    }
}
