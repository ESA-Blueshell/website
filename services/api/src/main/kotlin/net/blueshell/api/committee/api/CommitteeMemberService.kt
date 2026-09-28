package net.blueshell.api.committee.api

import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.committee.persistence.CommitteeMemberRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class CommitteeMemberService(
    private val repository: CommitteeMemberRepository,
    private val trackedEvents: TrackedEventPublisher,
) {
    /**
     * Gives up every seat the user currently holds. Each removal goes through
     * [delete] so it publishes its own [CommitteeMembershipChanged].
     */
    @Transactional
    fun revokeAllSeatsForUser(userId: Long) {
        repository.findByUser_Id(userId).forEach { delete(it) }
    }

    private fun delete(member: CommitteeMember) {
        val userId = member.userId
        val committeeId = member.committeeId
        repository.delete(member)
        trackedEvents.publish { actor -> CommitteeMembershipChanged(userId, committeeId, actor = actor) }
    }

    /**
     * Count the number of committee memberships for a user.
     * Used by other domains to check if a user has committee role.
     */
    @Transactional(readOnly = true)
    fun countMembershipsForUser(userId: Long): Long = repository.countByUser_Id(userId)

    /** Who sits on this committee now. */
    @Transactional(readOnly = true)
    fun findUserIdsOnCommittee(committeeId: Long): Set<Long> = repository.findUserIdsByCommitteeId(committeeId).toSet()

    /** Everybody who held any committee seat during the window, seats since left included. */
    fun findUserIdsSeatedBetween(
        from: Instant,
        to: Instant,
    ): Set<Long> = repository.findUserIdsWithSeatOverlapping(from, to).toSet()

    /**
     * One window per committee seat the user has held, soft-deleted rows included: a soft delete
     * is how an ended membership is recorded, and the cohort engine needs them for period
     * overlap. `leftAt` is the sentinel while a seat is held and the removal time otherwise. The
     * native query is what bypasses the entity's `@SQLRestriction`.
     */
    fun findMembershipWindowsForUser(userId: Long): List<CommitteeMembershipWindow> =
        repository.findWindowsByUserId(userId).map { row ->
            CommitteeMembershipWindow(
                committeeId = (row[0] as Number).toLong(),
                joinedAt = toInstant(row[1]),
                leftAt = toInstant(row[2]),
            )
        }

    /**
     * MariaDB's JDBC driver returns DATETIME columns as `java.time.LocalDateTime`
     * by default, but legacy connector versions and some pooling layers still
     * hand back `java.sql.Timestamp`. Either way the value carries no zone
     * information, so we treat it as already-UTC (which matches how the
     * persistence layer writes Instants today).
     */
    private fun toInstant(value: Any?): java.time.Instant =
        when (value) {
            is java.time.LocalDateTime -> value.toInstant(java.time.ZoneOffset.UTC)
            is java.time.OffsetDateTime -> value.toInstant()
            is java.sql.Timestamp -> value.toInstant()
            is java.util.Date -> value.toInstant()
            else -> throw IllegalStateException("Unexpected datetime value type: ${value?.javaClass?.name}")
        }
}
