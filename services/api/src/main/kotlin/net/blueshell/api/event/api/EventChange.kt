package net.blueshell.api.event.api

/** What happened to an event. An edit that also turns its approval says so rather than [UPDATED]. */
enum class EventChange {
    CREATED,
    UPDATED,
    APPROVED,

    /** An edit by somebody not on the board sent the approved event back to it. */
    SENT_BACK,
    UNAPPROVED,
    DELETED,
}
