package net.blueshell.api.membership.application.event

data class MembershipChangedEvent(
    val userId: Long,
    val active: Boolean,
    val changeType: MembershipChangeType
)
