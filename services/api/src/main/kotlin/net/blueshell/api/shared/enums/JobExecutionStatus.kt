package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class JobExecutionStatus {
    QUEUED,
    RUNNING,
    SUCCESS,

    /** Ran without an error and found nothing it should do; the execution says why. */
    SKIPPED,
    FAILED,
    DEAD
}
