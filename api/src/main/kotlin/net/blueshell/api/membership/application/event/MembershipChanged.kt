package net.blueshell.api.membership.application.event

data class MembershipChanged(
    val userId: Long,
    val active: Boolean,
    val changeType: MembershipChange
)
