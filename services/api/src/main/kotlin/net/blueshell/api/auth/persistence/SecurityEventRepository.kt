package net.blueshell.api.auth.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface SecurityEventRepository : BaseRepository<SecurityEvent, Long> {
    /** One event with the people it names, so an email can be written outside a transaction. */
    @Query("SELECT e FROM SecurityEvent e JOIN FETCH e.subject LEFT JOIN FETCH e.actor WHERE e.id = :id")
    fun findWithPeopleById(
        @Param("id") id: Long,
    ): SecurityEvent?

    @Query(
        value = """
        SELECT e FROM SecurityEvent e LEFT JOIN FETCH e.actor
        WHERE e.subject.id = :subjectId
        ORDER BY e.occurredAt DESC, e.id DESC
        """,
        countQuery = "SELECT COUNT(e) FROM SecurityEvent e WHERE e.subject.id = :subjectId",
    )
    fun findBySubjectNewestFirst(
        @Param("subjectId") subjectId: Long,
        pageable: Pageable,
    ): Page<SecurityEvent>

    @Query(
        """
        SELECT COUNT(e) > 0 FROM SecurityEvent e
        WHERE e.subject.id = :subjectId AND e.kind = net.blueshell.api.auth.persistence.SecurityEventKind.SIGNED_IN
        AND e.browserFamily = :family AND e.browserPlatform = :platform
        """,
    )
    fun hasSignedInFrom(
        @Param("subjectId") subjectId: Long,
        @Param("family") family: String,
        @Param("platform") platform: String,
    ): Boolean

    @Query(
        """
        SELECT COUNT(e) > 0 FROM SecurityEvent e
        WHERE e.subject.id = :subjectId AND e.kind = net.blueshell.api.auth.persistence.SecurityEventKind.SIGNED_IN
        """,
    )
    fun hasSignedIn(
        @Param("subjectId") subjectId: Long,
    ): Boolean

    /** Gone for good rather than soft-deleted: the twelve months are the whole of their keeping. */
    @Modifying
    @Query(value = "DELETE FROM security_events WHERE occurred_at < :before", nativeQuery = true)
    fun purgeOlderThan(
        @Param("before") before: Instant,
    ): Int

}
