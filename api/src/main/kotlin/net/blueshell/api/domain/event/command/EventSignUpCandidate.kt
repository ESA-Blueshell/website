package net.blueshell.api.domain.event.command

/**
 * Interface for commands that contain event sign-up data.
 * Used by validators to access sign-up information.
 */
interface EventSignUpCandidate {
    val data: EventSignUpData
}
