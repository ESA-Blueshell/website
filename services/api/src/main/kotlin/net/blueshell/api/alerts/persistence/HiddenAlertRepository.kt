package net.blueshell.api.alerts.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * Every write is one statement that may run twice: the sidebar and the page read and hide at the
 * same moment, and a row read first and written after would fail the second of them.
 */
interface HiddenAlertRepository : JpaRepository<HiddenAlert, Long> {
    fun findAllByUserId(userId: Long): List<HiddenAlert>

    /** Hiding again moves the hide up to the alert's mark of that moment. */
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO hidden_alert (user_id, alert_key, hidden_mark, hidden_at)
            VALUES (:userId, :alertKey, :mark, :hiddenAt)
            ON DUPLICATE KEY UPDATE hidden_mark = VALUES(hidden_mark), hidden_at = VALUES(hidden_at)
        """,
    )
    fun hide(
        @Param("userId") userId: Long,
        @Param("alertKey") alertKey: String,
        @Param("mark") mark: Long,
        @Param("hiddenAt") hiddenAt: Instant,
    ): Int

    @Modifying
    @Query("delete from HiddenAlert h where h.userId = :userId and h.alertKey = :alertKey")
    fun show(
        @Param("userId") userId: Long,
        @Param("alertKey") alertKey: String,
    ): Int

    @Modifying
    @Query("delete from HiddenAlert h where h.userId = :userId and h.alertKey in :cleared")
    fun forget(
        @Param("userId") userId: Long,
        @Param("cleared") cleared: Collection<String>,
    ): Int
}
