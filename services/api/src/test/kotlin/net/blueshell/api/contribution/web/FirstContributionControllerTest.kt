package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.domain.FirstContribution
import net.blueshell.api.contribution.domain.FirstContributions
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

class FirstContributionControllerTest {
    private val firsts: FirstContributions = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val controller = FirstContributionController(firsts, currentUser)

    @Test
    fun `names the reader's first contribution, answers no content without one, and refuses nobody`() {
        val owed = FirstContribution(LocalDate.of(2026, 9, 10), 4, null, null, null, null)
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(1L, setOf(Role.GUEST), null))
        whenever(firsts.owedBy(1L)).thenReturn(owed)
        assertThat(controller.findOwnFirstContribution().body).isEqualTo(owed)

        whenever(firsts.owedBy(1L)).thenReturn(null)
        assertThat(controller.findOwnFirstContribution().statusCode).isEqualTo(HttpStatus.NO_CONTENT)

        whenever(currentUser.currentUser()).thenReturn(null)
        assertThatThrownBy { controller.findOwnFirstContribution() }.isInstanceOf(ResponseStatusException::class.java)
    }
}
