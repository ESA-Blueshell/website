package net.blueshell.api.contribution.application.event

data class ContributionChanged(
    val userId: Long,
    val periodId: Long,
    val changeType: ContributionChange
)
