package net.blueshell.api.committee.application.event

data class CommitteeMembershipChanged(
    val userId: Long,
    val committeeId: Long
)
