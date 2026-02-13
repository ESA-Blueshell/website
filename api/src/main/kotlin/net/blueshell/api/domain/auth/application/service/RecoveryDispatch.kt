package net.blueshell.api.domain.auth.application.service

import net.blueshell.api.shared.enums.ResetType

data class RecoveryDispatch(
    val userId: Long,
    val rawToken: String,
    val type: ResetType
)
