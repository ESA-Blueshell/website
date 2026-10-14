package net.blueshell.api.mail.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface InboxMessageRepository : JpaRepository<InboxMessage, Long> {
    fun existsByMessageId(messageId: String): Boolean

    /** Narrowed by a word in the sender, the address it went to or the subject; the page says the order. */
    @Query(
        """
        select m from InboxMessage m
        where :search is null
           or lower(m.fromAddress) like lower(concat('%', :search, '%')) escape '!'
           or lower(coalesce(m.fromName, '')) like lower(concat('%', :search, '%')) escape '!'
           or lower(coalesce(m.toAddress, '')) like lower(concat('%', :search, '%')) escape '!'
           or lower(m.subject) like lower(concat('%', :search, '%')) escape '!'
        """,
    )
    fun search(
        @Param("search") search: String?,
        pageable: Pageable,
    ): Page<InboxMessage>

    fun countByStateAndAutomaticFalse(state: InboxState): Long

    fun countByAutomaticTrue(): Long

    fun findFirstByStateAndAutomaticFalseOrderByReceivedAtAsc(state: InboxState): InboxMessage?
}

@Repository
interface InboxCursorRepository : JpaRepository<InboxCursor, String>
