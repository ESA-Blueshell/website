package net.blueshell.api.declaration.web

import jakarta.validation.Validation
import net.blueshell.api.declaration.domain.BookingChoice
import net.blueshell.api.declaration.domain.Declarations
import net.blueshell.api.declaration.domain.DraftContent
import net.blueshell.api.declaration.domain.ExpenseContent
import net.blueshell.api.declaration.domain.Receipts
import net.blueshell.api.declaration.persistence.Booking
import net.blueshell.api.declaration.persistence.BookingKind
import net.blueshell.api.declaration.persistence.Declaration
import net.blueshell.api.declaration.persistence.DeclarationExpense
import net.blueshell.api.declaration.persistence.DeclarationState
import net.blueshell.api.declaration.persistence.DeclarationStep
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.util.ReflectionTestUtils.setField
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

class DeclarationControllerTest {
    private val declarations: Declarations = mock()
    private val receipts: Receipts = mock()
    private val users: UserService = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val controller = DeclarationController(declarations, receipts, DeclarationResponses(users, receipts), currentUser)
    private val reader = CurrentUser(12, emptySet(), null)

    private val declaration =
        Declaration(12, Instant.EPOCH).apply {
            setField(this, "id", 40L)
            confirmed = true
            steps += DeclarationStep(DeclarationState.DRAFT, Instant.EPOCH, 12)
            steps += DeclarationStep(DeclarationState.DRAFT, Instant.EPOCH, 99, "The receipt is missing.")
            receiptFileIds += 502
            replaceExpenses(
                listOf(
                    DeclarationExpense(this, 0, PAID_ON, "Snacks", 4835, Booking(BookingKind.EVENT, 7, "Autumn LAN party"))
                        .apply {
                            setField(this, "id", 41L)
                            receiptFileIds += 501
                            receiptFileIds += 503
                        },
                ),
            )
        }

    init {
        whenever(currentUser.currentUser()).thenReturn(reader)
        whenever(users.findAllByIds(any())).thenReturn(listOf(Entities.user(id = 12, firstName = "Ann", lastName = "Vos")))
        whenever(receipts.describe(any())).thenReturn(
            mapOf(
                501L to Entities.file(id = 501, type = FileType.RECEIPT, name = "bill.webp"),
                502L to Entities.file(id = 502, type = FileType.RECEIPT, name = "shop.pdf", mediaType = "application/pdf"),
            ),
        )
    }

    @Test
    fun `answers the reader's declarations with names, bookings and where each receipt opens`() {
        whenever(declarations.own(12)).thenReturn(listOf(declaration))

        val answer = controller.listOwnDeclarations().single()

        assertThat(answer.submitter).isEqualTo("Ann Vos")
        assertThat(answer.steps.map { it.by }).containsExactly("Ann Vos", null)
        assertThat(answer.steps.last().reason).isEqualTo("The receipt is missing.")
        assertThat(answer.receipts.single()).isEqualTo(ReceiptResponse(502, "shop.pdf", "application/pdf", "/declarations/receipts/502"))
        val expense = answer.expenses.single()
        assertThat(expense.bookedTo?.name).isEqualTo("Autumn LAN party")
        assertThat(expense.bookedTo).isEqualTo(BookingResponse(BookingKind.EVENT, 7, "Autumn LAN party"))
        // A receipt whose file is gone is left out rather than drawn broken.
        assertThat(expense.receipts.map { it.id }).containsExactly(501)
    }

    @Test
    fun `hands each write to the reader's declarations, as the content the request holds`() {
        whenever(declarations.ownOne(12, 40)).thenReturn(declaration)
        whenever(declarations.start(any(), any())).thenReturn(declaration)
        whenever(declarations.save(any(), any(), any())).thenReturn(declaration)
        whenever(declarations.submit(12, 40)).thenReturn(declaration)
        whenever(declarations.withdraw(12, 40)).thenReturn(declaration)
        val request =
            DeclarationDraftRequest(
                expenses = listOf(ExpenseRequest(PAID_ON, "Snacks", 4835, BookingRequest(BookingKind.EVENT, 7), listOf(501))),
                receipts = listOf(502),
                confirmed = true,
            )
        val content =
            DraftContent(
                listOf(ExpenseContent(PAID_ON, "Snacks", 4835, BookingChoice(BookingKind.EVENT, 7), listOf(501))),
                listOf(502),
                true,
            )

        assertThat(controller.findOwnDeclaration(40).id).isEqualTo(40)
        controller.startDeclaration(request)
        controller.saveDeclarationDraft(40, request)
        controller.submitDeclaration(40)
        controller.withdrawDeclaration(40)
        controller.deleteDeclarationDraft(40)

        verify(declarations).start(12, content)
        verify(declarations).save(12, 40, content)
        verify(declarations).delete(12, 40)
        val validator = Validation.buildDefaultValidatorFactory().validator
        assertThat(validator.validate(request)).isEmpty()
        assertThat(DeclarationDraftRequest().asContent()).isEqualTo(DraftContent(emptyList(), emptyList(), false))
        assertThat(ExpenseRequest().asContent()).isEqualTo(ExpenseContent(null, "", null, null, emptyList()))
        assertThat(validator.validate(request.copy(expenses = listOf(ExpenseRequest(amountCents = -1))))).isNotEmpty
    }

    @Test
    fun `a receipt is stored on upload and opened inline, a photo with no room to run script`() {
        val photo = Entities.file(id = 501, type = FileType.RECEIPT, name = "bill.webp")
        val pdf = Entities.file(id = 502, type = FileType.RECEIPT, name = "shop.pdf", mediaType = "application/pdf")
        val upload = MockMultipartFile("file", "bill.png", "image/png", byteArrayOf(1))
        whenever(receipts.store(upload)).thenReturn(photo)
        whenever(receipts.open(reader, 501)).thenReturn(photo)
        whenever(receipts.open(reader, 502)).thenReturn(pdf)
        whenever(receipts.contentOf(any())).thenAnswer { byteArrayOf(1, 2).inputStream() }

        assertThat(controller.uploadReceipt(upload).url).isEqualTo("/declarations/receipts/501")
        val shown = controller.openReceipt(501)
        assertThat(shown.headers.contentType.toString()).isEqualTo("image/webp")
        assertThat(shown.headers.getFirst("Content-Security-Policy")).contains("sandbox")
        assertThat(shown.headers.getFirst("Content-Disposition")).startsWith("inline")
        assertThat(controller.openReceipt(502).headers.getFirst("Content-Security-Policy")).isNull()

        whenever(currentUser.currentUser()).thenReturn(null)
        assertThatThrownBy { controller.openReceipt(501) }.isInstanceOf(ResponseStatusException::class.java)
    }

    private companion object {
        val PAID_ON: LocalDate = LocalDate.of(2026, 10, 1)
    }
}
