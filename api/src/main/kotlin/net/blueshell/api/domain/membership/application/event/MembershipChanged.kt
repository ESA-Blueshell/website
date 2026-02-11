package net.blueshell.api.domain.membership.application.event

data class MembershipChanged(
    val userId: Long,
    val active: Boolean,
    val changeType: MembershipChange
)
