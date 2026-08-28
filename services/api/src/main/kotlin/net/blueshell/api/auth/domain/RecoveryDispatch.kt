package net.blueshell.api.auth.domain

import net.blueshell.api.shared.enums.TokenPurpose

data class RecoveryDispatch(
    val userId: Long,
    val rawToken: String,
    val type: TokenPurpose
)
