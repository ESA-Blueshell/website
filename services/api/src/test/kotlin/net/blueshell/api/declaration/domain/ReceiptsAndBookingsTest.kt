package net.blueshell.api.declaration.domain

import net.blueshell.api.committee.api.CommitteeNotFound
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.declaration.persistence.Booking
import net.blueshell.api.declaration.persistence.BookingKind
import net.blueshell.api.declaration.persistence.Declaration
import net.blueshell.api.declaration.persistence.DeclarationRepository
import net.blueshell.api.declaration.persistence.DeclarationState
import net.blueshell.api.declaration.persistence.DeclarationStep
import net.blueshell.api.event.api.EventService
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.util.ReflectionTestUtils.setField
import java.time.Instant

class ReceiptsAndBookingsTest {
    private val files: FileService = mock()
    private val repository: DeclarationRepository = mock()
    private val receipts = Receipts(files, repository)

    private fun user(id: Long) =
        User(username = "user$id", email = "user$id@example.com", password = "hash", initials = "U", firstName = "User", lastName = "$id")
            .apply { setField(this, "id", id) }

    private fun receipt(uploader: Long) = File("bill.webp", "receipts/abc.webp", user(uploader), "image/webp", type = FileType.RECEIPT)

    private fun declaration(
        userId: Long,
        submitted: Boolean,
    ) = Declaration(userId, Instant.EPOCH).apply {
        if (submitted) {
            state = DeclarationState.SUBMITTED
            steps += DeclarationStep(DeclarationState.SUBMITTED, Instant.EPOCH, userId)
        }
    }

    private fun reader(
        id: Long,
        vararg roles: Role,
    ) = CurrentUser(id, roles.toSet(), null)

    @Test
    fun `a receipt is opened by the submitter who uploaded it and, while it is past draft, the board`() {
        whenever(files.findStored(501, FileType.RECEIPT)).thenReturn(receipt(uploader = 13))
        whenever(repository.findAllHoldingReceipt(501)).thenReturn(listOf(declaration(13, submitted = false)))

        assertThat(receipts.open(reader(13), 501).name).isEqualTo("bill.webp")
        assertThatThrownBy { receipts.open(reader(12), 501) }.isInstanceOf(ReceiptNotFound::class.java)
        assertThatThrownBy { receipts.open(reader(14, Role.BOARD), 501) }.isInstanceOf(ReceiptNotFound::class.java)
        assertThatThrownBy { receipts.open(reader(14), 502) }.isInstanceOf(ReceiptNotFound::class.java)

        whenever(repository.findAllHoldingReceipt(501)).thenReturn(listOf(declaration(13, submitted = true)))
        assertThat(receipts.open(reader(14, Role.BOARD), 501).name).isEqualTo("bill.webp")
        assertThatThrownBy { receipts.open(reader(15, Role.MEMBER), 501) }.isInstanceOf(ReceiptNotFound::class.java)
    }

    @Test
    fun `a receipt is attached only by whoever uploaded it, stored as its own kind, and deleted once nothing holds it`() {
        whenever(files.findStored(501, FileType.RECEIPT)).thenReturn(receipt(uploader = 12))
        whenever(repository.findAllHoldingReceipt(501)).thenReturn(listOf(declaration(12, submitted = false)))

        assertThat(receipts.mayAttach(12, 501)).isTrue()
        assertThat(receipts.mayAttach(13, 501)).isFalse()
        assertThat(receipts.mayAttach(12, 502)).isFalse()
        assertThat(receipts.describe(listOf(501, 501, 502)).keys).containsExactly(501)

        val upload = MockMultipartFile("file", "bill.png", "image/png", byteArrayOf(1))
        whenever(files.storeMultipart(upload, FileType.RECEIPT)).thenReturn(receipt(uploader = 12))
        assertThat(receipts.store(upload).type).isEqualTo(FileType.RECEIPT)
        whenever(files.openContent(org.mockito.kotlin.any())).thenReturn(byteArrayOf(7).inputStream())
        assertThat(receipts.contentOf(receipt(uploader = 12)).readAllBytes()).containsExactly(7)

        receipts.release(listOf(501))
        verify(files, never()).delete(org.mockito.kotlin.any())
        whenever(repository.findAllHoldingReceipt(501)).thenReturn(emptyList())
        receipts.release(listOf(501, 502))
        verify(files).delete(org.mockito.kotlin.any())
    }

    @Test
    fun `an expense is booked to a committee or event that exists, under its name`() {
        val committees: CommitteeService = mock()
        val events: EventService = mock()
        val bookings = Bookings(committees, events)
        whenever(committees.findById(3)).thenReturn(Committee(name = "Promo Committee", description = "Promo"))
        whenever(committees.findById(4)).thenThrow(CommitteeNotFound(4))

        assertThat(bookings.resolve(BookingChoice(BookingKind.COMMITTEE, 3)))
            .isEqualTo(Booking(BookingKind.COMMITTEE, 3, "Promo Committee"))
        assertThatThrownBy { bookings.resolve(BookingChoice(BookingKind.COMMITTEE, 4)) }.isInstanceOf(UnknownBooking::class.java)
        assertThatThrownBy { bookings.resolve(BookingChoice(BookingKind.EVENT, 7)) }.isInstanceOf(UnknownBooking::class.java)
        whenever(events.findByIdIncludingDeletedOrNull(8)).thenReturn(Entities.event(id = 8, title = "Autumn LAN party", approved = true))
        whenever(events.findByIdIncludingDeletedOrNull(9)).thenReturn(Entities.event(id = 9, title = "Surprise party"))
        assertThatThrownBy { bookings.resolve(BookingChoice(BookingKind.EVENT, 9)) }.isInstanceOf(UnknownBooking::class.java)
        assertThat(bookings.resolve(BookingChoice(BookingKind.EVENT, 8)).name).isEqualTo("Autumn LAN party")
    }
}
