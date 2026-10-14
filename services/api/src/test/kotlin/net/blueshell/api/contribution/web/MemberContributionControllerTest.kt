package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.domain.MemberContributions
import net.blueshell.api.contribution.domain.OwnContributions
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException

class MemberContributionControllerTest {
    private val contributions: MemberContributions = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val bank = BankProperties(iban = "NL00 TEST 0000 0000 00", bic = "TESTNL2A", accountName = "Test Vereniging")
    private val controller = MemberContributionController(contributions, currentUser, bank)

    @Test
    fun `answers the reader's own contributions, and refuses nobody signed in`() {
        val own = OwnContributions(held = true, paysByIncasso = false, periods = emptyList())
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(7L, setOf(Role.GUEST), null))
        whenever(contributions.of(7L)).thenReturn(own)
        assertThat(controller.findOwnContributions()).isEqualTo(own)

        whenever(currentUser.currentUser()).thenReturn(null)
        assertThatThrownBy { controller.findOwnContributions() }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `answers the account a member transfers the contribution to`() {
        assertThat(controller.findBankAccount()).isEqualTo(BankAccountResponse("NL00 TEST 0000 0000 00", "TESTNL2A", "Test Vereniging"))
    }
}
