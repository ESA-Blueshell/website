package net.blueshell.api.declaration.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface DeclarationRepository : JpaRepository<Declaration, Long> {
    fun findAllByUserIdOrderByCreatedAtDescIdDesc(userId: Long): List<Declaration>

    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): Declaration?

    fun findAllByStateNotOrderByCreatedAtDescIdDesc(state: DeclarationState): List<Declaration>

    /** The declarations a receipt is on, as one of their own or one of an expense's. */
    @Query(
        """
        SELECT DISTINCT d FROM Declaration d
        LEFT JOIN d.receiptFileIds own
        LEFT JOIN d._expenses e
        LEFT JOIN e.receiptFileIds spent
        WHERE own = :fileId OR spent = :fileId
        """,
    )
    fun findAllHoldingReceipt(fileId: Long): List<Declaration>
}
