package net.blueshell.api.declaration.persistence

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import jakarta.persistence.Version
import net.blueshell.api.shared.model.AutoIdEntity
import org.hibernate.annotations.BatchSize
import java.time.Instant

/** A person's request to be paid back what they spent for the association (api ADR-041). */
@Entity
@Table(name = "declarations")
class Declaration(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) : AutoIdEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    var state: DeclarationState = DeclarationState.DRAFT

    /** The submitter's statement that the expenses were for the association and the receipts are real. */
    @Column(name = "confirmed", nullable = false)
    var confirmed: Boolean = false

    // A board member and the submitter may act on the same declaration at once.
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L

    @OneToMany(mappedBy = "declaration", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    private val _expenses: MutableList<DeclarationExpense> = mutableListOf()

    val expenses: List<DeclarationExpense>
        get() = _expenses

    /** Puts [written] in place of every expense it held; their receipts go with them. */
    fun replaceExpenses(written: List<DeclarationExpense>) {
        _expenses.clear()
        _expenses += written
    }

    /** The receipts for the declaration as a whole, rather than for one expense. */
    @ElementCollection
    @CollectionTable(name = "declaration_receipts", joinColumns = [JoinColumn(name = "declaration_id")])
    @OrderColumn(name = "position")
    @Column(name = "file_id", nullable = false)
    @BatchSize(size = 50)
    val receiptFileIds: MutableList<Long> = mutableListOf()

    /** Every change of state, oldest first. */
    @ElementCollection
    @CollectionTable(name = "declaration_steps", joinColumns = [JoinColumn(name = "declaration_id")])
    @OrderColumn(name = "position")
    @BatchSize(size = 50)
    val steps: MutableList<DeclarationStep> = mutableListOf()

    /** Every receipt it holds, its own and its expenses'. */
    val allReceiptFileIds: List<Long>
        get() = receiptFileIds + expenses.flatMap { it.receiptFileIds }
}

@Schema(enumAsRef = true)
enum class DeclarationState { DRAFT, SUBMITTED, APPROVED, PAID, REJECTED }
