package net.blueshell.api.user.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface MemberProfileRepository : BaseRepository<MemberProfile, Long> {
    fun findByUser_Id(userId: Long): Optional<MemberProfile>

    /**
     * Which of these members have said their name may be published on the team pages.
     *
     * Answered for a whole page at once rather than a member at a time, and by asking who
     * consented rather than who did not: a member with no profile row at all has consented to
     * nothing, and comes back absent either way.
     */
    @Query(
        """
        SELECT p.user.id FROM MemberProfile p
        WHERE p.user.id IN :userIds AND p.nameOnTeamPages = true
        """,
    )
    fun findUserIdsConsentingToNameOnTeamPages(@Param("userIds") userIds: Collection<Long>): List<Long>
}
