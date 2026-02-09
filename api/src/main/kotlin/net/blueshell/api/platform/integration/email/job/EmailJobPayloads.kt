package net.blueshell.api.platform.integration.email.job

import net.blueshell.api.shared.enums.ResetType

data class RecoveryEmailPayload(
    val userId: Long,
    val token: String,
    val resetType: ResetType
)

data class EventSignupEmailPayload(val eventSignUpId: Long)

data class ContributionReminderEmailPayload(
    val userId: Long,
    val contributionPeriodId: Long
)
