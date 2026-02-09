package net.blueshell.api.platform.integration.event.job

data class ContributionReminderEmailEvent(
    val userId: Long?,
    val contributionPeriodId: Long?
)
