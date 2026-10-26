package net.blueshell.api.shared.job

import io.swagger.v3.oas.annotations.media.Schema

/** What a run counted, for the jobs page to put in words. */
@Schema(enumAsRef = true)
enum class JobTally {
    PLACES_READ,
    MESSAGES_READ,
    EMOJI_SEEN,
    PLACES_LEFT,
    SHORTCODES_RESOLVED,
    SHORTCODES_LEFT,
    EVENTS_QUEUED,
}

/**
 * One count in what a run reports about its work: [channels] are the Discord channels it names,
 * by id, and [names] the other things it names, as they are written.
 */
@Schema(name = "JobCount")
data class JobCount(
    val tally: JobTally,
    val count: Long,
    val channels: List<String> = emptyList(),
    val names: List<String> = emptyList(),
)
