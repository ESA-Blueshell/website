package net.blueshell.api.domain.auth.persistence.repository

import net.blueshell.api.domain.auth.persistence.RecoveryToken
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface RecoveryTokenRepository : BaseRepository<RecoveryToken, Long> {
    fun findBySelector(selector: String): Optional<RecoveryToken>

    @Query(
        """
        select rt from RecoveryToken rt
        where rt.user.id = :userId
        and rt.consumedAt is null
    """
    )
    fun findAllUnconsumedByUserId(userId: Long): MutableList<RecoveryToken>

    @Query(
        """
        select rt from RecoveryToken rt
        where rt.user.id = :userId
        and rt.type = :type
        and rt.consumedAt is null
    """
    )
    fun findAllUnconsumedByTypeAndUserId(userId: Long, type: TokenPurpose): MutableList<RecoveryToken>

    /**
     * Ids of every account holding an unconsumed token of this kind, expired or not: the
     * question is which activation an account takes, not whether its last link still works.
     */
    @Query(
        """
        select distinct rt.user.id from RecoveryToken rt
        where rt.type = :type
        and rt.consumedAt is null
    """
    )
    fun findUserIdsWithUnconsumedType(type: TokenPurpose): List<Long>
}
