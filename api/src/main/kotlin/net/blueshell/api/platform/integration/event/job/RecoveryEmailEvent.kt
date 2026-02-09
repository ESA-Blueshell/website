package net.blueshell.api.platform.integration.event.job

import net.blueshell.api.shared.enums.ResetType


data class RecoveryEmailEvent(val userId: Long, val token: String, val resetType: ResetType)
