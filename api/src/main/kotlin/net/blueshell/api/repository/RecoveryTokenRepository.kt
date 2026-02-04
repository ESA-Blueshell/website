package net.blueshell.api.repository

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.common.enums.ResetType
import net.blueshell.api.model.RecoveryToken
import java.util.*

interface RecoveryTokenRepository : BaseRepository<RecoveryToken, Long> {
    fun findBySelector(selector: String): Optional<RecoveryToken>

    fun findAllByUser_IdAndTypeAndConsumedAtIsNull(userId: Long, type: ResetType): MutableList<RecoveryToken>

    fun findAllByUser_IdAndConsumedAtIsNull(userId: Long): MutableList<RecoveryToken>
}