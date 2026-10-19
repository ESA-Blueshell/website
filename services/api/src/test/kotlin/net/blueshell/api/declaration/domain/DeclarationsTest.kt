package net.blueshell.api.declaration.domain

import net.blueshell.api.declaration.persistence.Booking
import net.blueshell.api.declaration.persistence.BookingKind
import net.blueshell.api.declaration.persistence.Declaration
import net.blueshell.api.declaration.persistence.DeclarationRepository
import net.blueshell.api.declaration.persistence.DeclarationState
import net.blueshell.api.user.api.BankAccountLookup
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils.setField
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DeclarationsTest {
    // Half past midnight in Enschede, still the day before in UTC.
    private val now = Instant.parse("2026-10-14T22:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repository: DeclarationRepository = mock()
    private val receipts: Receipts = mock()
    private val bookings: Bookings = mock()
    private val bankAccounts: BankAccountLookup = mock()
    private val declarations = Declarations(repository, receipts, bookings, bankAccounts, clock)
    private val held = mutableMapOf<Long, Declaration>()

    init {
        whenever(repository.save(any<Declaration>())).thenAnswer { saving ->
            (saving.arguments[0] as Declaration).also {
                setField(it, "id", (held.size + 1).toLong())
                held[it.id!!] = it
            }
        }
        whenever(repository.findByIdAndUserId(any(), any())).thenAnswer { asked ->
            held[asked.arguments[0] as Long]?.takeIf { it.userId == asked.arguments[1] }
        }
        whenever(receipts.mayAttach(12, 501)).thenReturn(true)
        whenever(receipts.mayAttach(12, 502)).thenReturn(true)
        whenever(bookings.resolve(BookingChoice(BookingKind.EVENT, 7))).thenReturn(Booking(BookingKind.EVENT, 7, "Autumn LAN party"))
        whenever(bankAccounts.isOnFile(12)).thenReturn(true)
    }

    private fun expense(
        paidOn: LocalDate? = LocalDate.of(2026, 10, 15),
        description: String = "Pizza for the committee meeting",
        amountCents: Int? = 1250,
        receipts: List<Long> = listOf(501),
    ) = ExpenseContent(paidOn, description, amountCents, null, receipts)

    private fun draft(
        vararg expenses: ExpenseContent = arrayOf(expense()),
        receipts: List<Long> = emptyList(),
        confirmed: Boolean = true,
    ) = DraftContent(expenses.toList(), receipts, confirmed)

    @Test
    fun `a draft keeps whatever it was given, and is submitted only once it is complete, confirmed and payable`() {
        val empty = expense(paidOn = null, description = " ", amountCents = null, receipts = emptyList())
        val started = declarations.start(12, draft(empty, confirmed = false))

        assertThat(started.state).isEqualTo(DeclarationState.DRAFT)
        assertThat(started.steps.single().byUserId).isEqualTo(12)
        assertThat(started.createdAt).isEqualTo(now)
        val id = started.id!!

        val refusals =
            listOf(
                draft(*emptyArray()) to NoExpense::class.java,
                draft(expense(paidOn = null)) to ExpenseIncomplete::class.java,
                draft(expense(paidOn = LocalDate.of(2026, 10, 16))) to PaidInFuture::class.java,
                draft(expense(amountCents = 0)) to ExpenseIncomplete::class.java,
                draft(expense(description = "  ")) to ExpenseIncomplete::class.java,
                draft(expense(receipts = emptyList())) to NoReceipt::class.java,
                draft(confirmed = false) to NotConfirmed::class.java,
            )
        refusals.forEach { (content, refusal) ->
            declarations.save(12, id, content)
            assertThatThrownBy { declarations.submit(12, id) }.isInstanceOf(refusal)
        }
        declarations.save(12, id, draft())
        whenever(bankAccounts.isOnFile(12)).thenReturn(false)
        assertThatThrownBy { declarations.submit(12, id) }.isInstanceOf(NoBankAccount::class.java)

        whenever(bankAccounts.isOnFile(12)).thenReturn(true)
        val submitted = declarations.submit(12, id)

        assertThat(submitted.state).isEqualTo(DeclarationState.SUBMITTED)
        assertThat(submitted.steps.map { it.state }).containsExactly(DeclarationState.DRAFT, DeclarationState.SUBMITTED)
        assertThat(submitted.expenses.single().description).isEqualTo("Pizza for the committee meeting")
        whenever(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(12)).thenReturn(listOf(submitted))
        assertThat(declarations.own(12)).containsExactly(submitted)
    }

    @Test
    fun `a receipt for the declaration as a whole is enough, and names what the incomplete expense misses`() {
        val id = declarations.start(12, draft(expense(receipts = emptyList()), receipts = listOf(502))).id!!

        assertThat(declarations.submit(12, id).state).isEqualTo(DeclarationState.SUBMITTED)

        val other = declarations.start(12, draft(expense(), expense(amountCents = null))).id!!
        assertThatThrownBy { declarations.submit(12, other) }
            .isInstanceOfSatisfying(ExpenseIncomplete::class.java) {
                assertThat(it.facts).isEqualTo(mapOf("expense" to 2, "field" to "amount"))
            }
    }

    @Test
    fun `only a draft is changed or deleted, and a submitted one is withdrawn to a draft to be confirmed again`() {
        val id = declarations.start(12, draft()).id!!
        assertThatThrownBy { declarations.withdraw(12, id) }.isInstanceOf(DeclarationNotSubmitted::class.java)
        declarations.submit(12, id)

        assertThatThrownBy { declarations.save(12, id, draft()) }.isInstanceOf(DeclarationNotDraft::class.java)
        assertThatThrownBy { declarations.delete(12, id) }.isInstanceOf(DeclarationNotDraft::class.java)
        assertThatThrownBy { declarations.submit(12, id) }.isInstanceOf(DeclarationNotDraft::class.java)

        val withdrawn = declarations.withdraw(12, id)

        assertThat(withdrawn.state).isEqualTo(DeclarationState.DRAFT)
        assertThat(withdrawn.confirmed).isFalse()
        assertThat(withdrawn.steps.map { it.state })
            .containsExactly(DeclarationState.DRAFT, DeclarationState.SUBMITTED, DeclarationState.DRAFT)
        declarations.delete(12, id)
        verify(repository).delete(withdrawn)
        verify(receipts).release(listOf(501L))
    }

    @Test
    fun `a declaration is its submitter's alone, and holds only receipts they may attach`() {
        val id = declarations.start(12, draft()).id!!

        assertThatThrownBy { declarations.ownOne(13, id) }.isInstanceOf(DeclarationNotFound::class.java)
        assertThatThrownBy { declarations.save(13, id, draft()) }.isInstanceOf(DeclarationNotFound::class.java)
        assertThatThrownBy { declarations.start(12, draft(expense(receipts = listOf(999)))) }.isInstanceOf(UnknownReceipt::class.java)
        assertThatThrownBy { declarations.save(12, id, draft(receipts = listOf(999))) }.isInstanceOf(UnknownReceipt::class.java)
    }

    @Test
    fun `a receipt taken off a draft is let go, and a committee or event kept on it keeps its name`() {
        val booked = ExpenseContent(LocalDate.of(2026, 10, 1), "Snacks", 4835, BookingChoice(BookingKind.EVENT, 7), listOf(501, 501))
        val id = declarations.start(12, draft(booked, receipts = listOf(502))).id!!
        val kept = declarations.ownOne(12, id).expenses.single()
        assertThat(kept.receiptFileIds).containsExactly(501)

        whenever(receipts.mayAttach(12, 501)).thenReturn(false)
        val saved = declarations.save(12, id, draft(booked))

        assertThat(saved.expenses.single().bookedTo).isEqualTo(Booking(BookingKind.EVENT, 7, "Autumn LAN party"))
        verify(bookings, times(1)).resolve(any())
        verify(receipts).release(listOf(502L))
        verify(receipts, never()).release(listOf(501L))
    }
}
