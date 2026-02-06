package net.blueshell.api.repository

import net.blueshell.api.common.enums.ResetType
import net.blueshell.api.model.RecoveryToken
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface RecoveryTokenRepository : BaseRepository<RecoveryToken, Long> {
    fun findBySelector(selector: String): Optional<RecoveryToken>

    @Query(
        """
        select rt from RecoveryToken rt 
        where rt._user.id = :userId 
        and rt.consumedAt is null
    """
    )
    fun findAllUnconsumedByUserId(userId: Long): MutableList<RecoveryToken>

    @Query(
        """
        select rt from RecoveryToken rt 
        where rt._user.id = :userId 
        and rt.type = :type 
        and rt.consumedAt is null
    """
    )
    fun findAllUnconsumedByTypeAndUserId(userId: Long, type: ResetType): MutableList<RecoveryToken>
}