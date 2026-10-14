package net.blueshell.api.sync.api

/** The channels an event's Discord posts go to, by name, as the board set them. */
interface DiscordPostChannels {
    /** Where an approved event is announced. */
    fun info(): String

    /** Where an event is posted on its day. */
    fun calendar(): String
}
