package net.blueshell.api.contribution.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
@Suppress("FunctionName")
interface IncassoNotificationRepository : BaseRepository<IncassoNotification, Long> {
    /** Every notification sent for this period. A member may appear more than once. */
    fun findByContributionPeriod_Id(contributionPeriodId: Long): MutableList<IncassoNotification>

    fun findByUser_Id(userId: Long): List<IncassoNotification>

    fun findByIncassoRunIdIn(incassoRunIds: Collection<Long>): List<IncassoNotification>

    /**
     * The latest date a member was collected from under a mandate: the debit date of a
     * notification in a run the board submitted to ING. Null where none was. Read past soft
     * deletion, as a removed notification was still a collection.
     */
    @Query(
        value =
            "SELECT MAX(n.debit_date) FROM incasso_notifications n JOIN incasso_runs r ON r.id = n.incasso_run_id " +
                "WHERE n.user_id = :userId AND n.mandate_reference = :reference AND r.submitted_at IS NOT NULL",
        nativeQuery = true,
    )
    fun lastCollectionDate(
        @Param("userId") userId: Long,
        @Param("reference") reference: String,
    ): LocalDate?
}
