package net.blueshell.api.pinger.api

import net.blueshell.api.pinger.persistence.PingerBoardMember
import net.blueshell.api.pinger.persistence.PingerBoardMemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * A member's own opt-in to the public contribution leaderboard. The default is off: a member is
 * shown only once they opt in, and opting out hides them again while keeping the row.
 */
@Service
class LeaderboardOptIns(
    private val members: PingerBoardMemberRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun isOptedIn(memberId: Long): Boolean = members.findByMemberId(memberId)?.optedIn ?: false

    @Transactional
    fun setOptedIn(
        memberId: Long,
        optedIn: Boolean,
    ) {
        val row =
            members.findByMemberId(memberId)
                ?: PingerBoardMember(memberId = memberId, optedIn = optedIn, updated = clock.instant())
        row.optedIn = optedIn
        row.updated = clock.instant()
        members.save(row)
    }
}
