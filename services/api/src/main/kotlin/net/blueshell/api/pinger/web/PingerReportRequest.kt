package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.PositiveOrZero

/**
 * A pinger's status report. [sent] is the client's own cumulative send counter, which resets to 0
 * when the app restarts; the server accrues the identity's durable tally from it rather than
 * trusting it as a running total. The counts are non-negative, and [pps] is capped at the sender's
 * own rate limit so a wild value cannot be mistaken for a real one.
 */
@Schema(description = "A pinger's status report")
data class PingerReportRequest(
    @field:Schema(description = "Whether the pinger is currently sending")
    val online: Boolean,
    @field:Schema(description = "The current send rate in pings per second")
    @field:PositiveOrZero
    @field:Max(200_000)
    val pps: Int,
    @field:Schema(description = "The client's own cumulative pings sent this session; resets to 0 on restart")
    @field:PositiveOrZero
    val sent: Long,
    @field:Schema(description = "The client's own count of send errors this session")
    @field:PositiveOrZero
    val errors: Int,
)
