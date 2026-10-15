package net.blueshell.api.declaration.persistence

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import org.hibernate.annotations.BatchSize
import java.time.LocalDate

/** One thing paid for on a declaration. A draft's expense may still miss its day, amount or words. */
@Entity
@Table(name = "declaration_expenses")
class DeclarationExpense(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "declaration_id", nullable = false)
    val declaration: Declaration,
    @Column(name = "position", nullable = false)
    val position: Int,
    @Column(name = "paid_on")
    val paidOn: LocalDate?,
    @Column(name = "description", nullable = false, length = DESCRIPTION_MAX)
    val description: String,
    @Column(name = "amount_cents")
    val amountCents: Int?,
    @Embedded
    val bookedTo: Booking?,
) : AutoIdEntity() {
    @ElementCollection
    @CollectionTable(name = "declaration_expense_receipts", joinColumns = [JoinColumn(name = "expense_id")])
    @OrderColumn(name = "position")
    @Column(name = "file_id", nullable = false)
    @BatchSize(size = 50)
    val receiptFileIds: MutableList<Long> = mutableListOf()

    companion object {
        const val DESCRIPTION_MAX = 1000
    }
}

/** The committee or event an expense was for, named as it was when the expense was saved. */
@Embeddable
data class Booking(
    @Enumerated(EnumType.STRING)
    @Column(name = "booked_to_kind", length = 16)
    val kind: BookingKind,
    @Column(name = "booked_to_id")
    val id: Long,
    @Column(name = "booked_to_name")
    val name: String,
)

@Schema(enumAsRef = true)
enum class BookingKind { COMMITTEE, EVENT }
