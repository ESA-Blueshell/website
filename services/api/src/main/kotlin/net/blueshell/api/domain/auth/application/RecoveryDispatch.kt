package net.blueshell.api.domain.auth.application

import net.blueshell.api.shared.enums.TokenPurpose

data class RecoveryDispatch(
    val userId: Long,
    val rawToken: String,
    val type: TokenPurpose
)
