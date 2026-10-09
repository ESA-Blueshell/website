package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import net.blueshell.api.pinger.api.MAX_REPORT_PPS

/**
 * A pinger's status report. [sent] is the reporting device's own cumulative send counter, which
 * resets to 0 when that device's app restarts; the server accrues the identity's durable tally from
 * the per-device delta rather than trusting it as a running total. [deviceId] is the stable
 * per-install id that keeps one device's counter apart from another's, so a member on several
 * devices accrues each exactly once. The counts are non-negative, and [pps] is capped at the
 * sender's own rate limit so a wild value cannot be mistaken for a real one.
 */
@Schema(description = "A pinger's status report")
data class PingerReportRequest(
    @field:Schema(
        description =
            "A stable per-install id for the reporting device, so its counter is kept apart " +
                "from the member's other devices",
    )
    @field:NotBlank
    @field:Size(max = 64)
    val deviceId: String,
    @field:Schema(description = "Whether the pinger is currently sending")
    val online: Boolean,
    @field:Schema(description = "The current send rate in pings per second")
    @field:PositiveOrZero
    @field:Max(MAX_REPORT_PPS)
    val pps: Int,
    @field:Schema(description = "The client's own cumulative pings sent this session; resets to 0 on restart")
    @field:PositiveOrZero
    // Far above any real session at the capped rate; blocks an absurd one-shot value topping the board.
    @field:Max(1_000_000_000_000_000)
    val sent: Long,
    @field:Schema(description = "The client's own count of send errors this session")
    @field:PositiveOrZero
    val errors: Int,
)
