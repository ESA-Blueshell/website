package net.blueshell.api.common.event.job

data class ContributionReminderEmailEvent(
    val userId: Long?,
    val contributionPeriodId: Long?
)
