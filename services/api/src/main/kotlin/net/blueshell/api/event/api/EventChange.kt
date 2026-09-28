package net.blueshell.api.event.api

/** What happened to an event. An edit that also turns its approval says so rather than [UPDATED]. */
enum class EventChange {
    CREATED,
    UPDATED,
    APPROVED,
    UNAPPROVED,
    DELETED,
}
