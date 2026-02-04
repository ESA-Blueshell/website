package net.blueshell.api.common.event.job

import net.blueshell.api.common.enums.ResetType


data class RecoveryEmailEvent(val userId: Long, val token: String, val resetType: ResetType)
