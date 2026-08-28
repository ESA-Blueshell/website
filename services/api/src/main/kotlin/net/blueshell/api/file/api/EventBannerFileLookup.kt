package net.blueshell.api.file.api

/**
 * Resolves which file holds an event's banner. Implemented by the event module,
 * which owns the association, so the file module does not reach into it.
 */
interface EventBannerFileLookup {
    fun fileIdForEvent(eventId: Long): Long?
}
