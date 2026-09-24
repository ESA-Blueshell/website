package net.blueshell.api.event.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** A Discord role an event notifies when the bot posts it, with its name as last known. */
@Embeddable
data class PingedRole(
    @Column(name = "role_id", nullable = false, length = 32)
    val roleId: String = "",
    @Column(name = "role_name", nullable = false, length = 100)
    val roleName: String = "",
)
