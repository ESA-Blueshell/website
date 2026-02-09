package net.blueshell.api.contribution.application.event

data class ContributionChangedEvent(
    val userId: Long,
    val periodId: Long,
    val changeType: ContributionChangeType
)
