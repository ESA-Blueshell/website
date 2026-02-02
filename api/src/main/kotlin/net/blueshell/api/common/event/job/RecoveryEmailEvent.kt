package net.blueshell.api.common.event.job

import net.blueshell.api.common.enums.ResetType

@JvmRecord
data class RecoveryEmailEvent(val userId: Long?, val token: String?, val resetType: ResetType?)
