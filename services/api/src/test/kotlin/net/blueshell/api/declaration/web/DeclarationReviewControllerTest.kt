package net.blueshell.api.declaration.web

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.declaration.domain.DeclarationReviews
import net.blueshell.api.declaration.domain.declarationOf
import net.blueshell.api.declaration.persistence.DeclarationRepository
import net.blueshell.api.declaration.persistence.DeclarationState
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.BankAccountLookup
import net.blueshell.api.user.api.BankAccountToPay
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.util.Optional

class DeclarationReviewControllerTest {
    private val repository: DeclarationRepository = mockk()
    private val users: UserService = mockk()
    private val currentUser: CurrentUserProvider = mockk()
    private val bankAccounts: BankAccountLookup = mockk()
    private val reviews = DeclarationReviews(repository, mockk(relaxed = true), bankAccounts, Clock.systemUTC())
    private val controller =
        DeclarationReviewController(reviews, DeclarationResponses(users, mockk(relaxed = true)), currentUser)

    init {
        every { repository.findById(40) } answers { Optional.of(declarationOf(40, DeclarationState.SUBMITTED, "Pizza" to 1250)) }
        every { repository.findAllByStateNotOrderByCreatedAtDescIdDesc(DeclarationState.DRAFT) } answers
            { listOf(declarationOf(40, DeclarationState.SUBMITTED, "Pizza" to 1250)) }
        every { currentUser.currentUser() } returns CurrentUser(3, emptySet(), null)
        every { users.findAllByIds(any()) } returns listOf(Entities.user(12, firstName = "Ann", lastName = "Vos"), Entities.user(3))
    }

    @Test
    fun `answers declarations with who submitted them, and each move as the board member made it`() {
        assertThat(controller.listDeclarations().map { it.id }).containsExactly(40L)
        assertThat(controller.findDeclaration(40).let { it.submitterId to it.submitter }).isEqualTo(12L to "Ann Vos")
        val approved = controller.approveDeclaration(40).steps.last()
        assertThat(approved.state to approved.by).isEqualTo(DeclarationState.APPROVED to "User 3")
        assertThat(controller.sendBackDeclaration(40, DeclarationReasonRequest("Cut off")).state).isEqualTo(DeclarationState.DRAFT)
        assertThat(controller.rejectDeclaration(40, DeclarationReasonRequest("No")).state).isEqualTo(DeclarationState.REJECTED)
        // Every field has a default, so a body that leaves one out is refused by validation rather than parsing.
        assertThat(DeclarationReasonRequest().reason).isEmpty()
    }

    @Test
    fun `a declaration is paid to the account on file, shown masked, and keeps that account's preview`() {
        every { bankAccounts.toPay(12) } returns BankAccountToPay("Ann Vos", MaskedIban("DE", "00"), "COBADEFFXXX")
        every { bankAccounts.recordPaidTo(12, any()) } returns MaskedIban("DE", "00")
        every { repository.findById(40) } returns Optional.of(declarationOf(40, DeclarationState.APPROVED, "Pizza" to 1250))

        val account = controller.findDeclarationBankAccount(40).bankAccount!!
        val read = listOf(account.accountHolder, account.ibanCountry, account.ibanLastTwo, account.bic)
        assertThat(read).containsExactly("Ann Vos", "DE", "00", "COBADEFFXXX")
        val paid = controller.markDeclarationPaid(40).steps.last()
        assertThat(listOf(paid.state, paid.paidToCountry, paid.paidToLastTwo)).containsExactly(DeclarationState.PAID, "DE", "00")
        every { bankAccounts.toPay(12) } returns null
        assertThat(controller.findDeclarationBankAccount(40).bankAccount).isNull()
    }

    @Test
    fun `a review without a signed-in board member is refused`() {
        every { currentUser.currentUser() } returns null
        assertThatThrownBy { controller.approveDeclaration(40) }.isInstanceOf(ResponseStatusException::class.java)
    }
}
