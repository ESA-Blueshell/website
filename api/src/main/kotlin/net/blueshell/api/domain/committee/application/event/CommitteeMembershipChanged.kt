package net.blueshell.api.domain.committee.application.event

data class CommitteeMembershipChanged(
    val userId: Long,
    val committeeId: Long
)
