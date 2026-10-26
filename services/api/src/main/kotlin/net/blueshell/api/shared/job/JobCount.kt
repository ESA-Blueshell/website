package net.blueshell.api.shared.job

import io.swagger.v3.oas.annotations.media.Schema

/** What a run counted, for the jobs page to put in words. */
@Schema(enumAsRef = true)
enum class JobTally {
    PLACES_READ,
    MESSAGES_READ,
    EMOJI_SEEN,
    PLACES_LEFT,
}

/** One count in what a run reports about its work; [channels] are the Discord channels it names, by id. */
@Schema(name = "JobCount")
data class JobCount(
    val tally: JobTally,
    val count: Long,
    val channels: List<String> = emptyList(),
)
