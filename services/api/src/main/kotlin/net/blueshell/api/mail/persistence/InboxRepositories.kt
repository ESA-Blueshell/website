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

    /** Newest first, narrowed by a word in the sender, the address it went to or the subject. */
    @Query(
        """
        select m from InboxMessage m
        where :search is null
           or lower(m.fromAddress) like lower(concat('%', :search, '%'))
           or lower(coalesce(m.fromName, '')) like lower(concat('%', :search, '%'))
           or lower(coalesce(m.toAddress, '')) like lower(concat('%', :search, '%'))
           or lower(m.subject) like lower(concat('%', :search, '%'))
        order by m.receivedAt desc, m.id desc
        """,
    )
    fun search(
        @Param("search") search: String?,
        pageable: Pageable,
    ): Page<InboxMessage>

    fun countByStateAndAutomaticFalse(state: InboxState): Long

    fun countByAutomaticTrue(): Long

    fun findFirstByStateAndAutomaticFalseOrderByReceivedAtAsc(state: InboxState): InboxMessage?

    /** What one address sent, newest first. */
    fun findTop20ByFromAddressOrderByReceivedAtDesc(fromAddress: String): List<InboxMessage>
}

@Repository
interface InboxCursorRepository : JpaRepository<InboxCursor, String>

@Repository
interface InboxReplyRepository : JpaRepository<InboxReply, Long> {
    fun findByInboxMessageIdInOrderByWrittenAtAsc(inboxMessageIds: Collection<Long>): List<InboxReply>
}
