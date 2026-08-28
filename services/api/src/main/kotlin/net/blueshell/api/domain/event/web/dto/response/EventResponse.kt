package net.blueshell.api.domain.event.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.survey.web.SurveyResponse
import java.time.Instant

@Schema(name = "EventResponse")
data class EventResponse(
    var id: Long,

    // Nullable when the owning Committee has been soft-deleted; the event row
    // is kept (signups, bookkeeping, etc.) but the link is severed. Live events
    // always carry a committee.
    var committeeId: Long?,

    @field:NotBlank(message = "Event title cannot be empty.")
    @field:Size(max = 255, message = "Event title cannot exceed 255 characters.")
    var title: String,

    // Nullable for the same reason the column is: older event rows predate the
    // create-time description requirement.
    var description: String?,

    var location: String? = null,

    @field:NotNull
    var startTime: Instant,

    @field:NotNull
    var endTime: Instant,

    var memberPrice: Double? = null,
    var publicPrice: Double? = null,

    @field:NotNull
    var approved: Boolean,

    @field:NotNull
    var membersOnly: Boolean,

    @field:NotNull
    var signUp: Boolean,

    var signUpDeadline: Instant? = null,
    var signUpLimit: Int? = null,

    var banner: EventBannerResponse? = null,

    @field:NotNull
    var signUpCount: Long,

    @field:Valid
    var signUpForm: SurveyResponse? = null,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
