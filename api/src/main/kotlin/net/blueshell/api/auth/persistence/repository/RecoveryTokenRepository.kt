package net.blueshell.api.auth.persistence.repository

import net.blueshell.api.auth.persistence.RecoveryToken
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

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
    fun findAllUnconsumedByTypeAndUserId(userId: Long, type: ResetType): MutableList<RecoveryToken>
}