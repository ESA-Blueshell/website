package net.blueshell.api.domain.event.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.domain.event.web.validation.HasSignUpDeadline
import net.blueshell.api.domain.event.web.validation.ValidSignUpDeadline
import net.blueshell.api.survey.web.SurveyRequest
import java.time.Instant

@Schema(name = "UpdateEventRequest")
@ValidSignUpDeadline
data class UpdateEventRequest(
    var committeeId: Long,

    @field:NotBlank(message = "Event title cannot be empty.")
    @field:Size(min = 1, max = 200, message = "Title must be 1-200 characters")
    var title: String,

    @field:NotBlank(message = "Event description cannot be empty.")
    @field:Size(min = 1, max = 4095, message = "Description must be 1-4095 characters")
    var description: String,

    var location: String? = null,

    var startTime: Instant,

    override var endTime: Instant,

    var memberPrice: Double? = null,
    var publicPrice: Double? = null,

    var approved: Boolean,

    var membersOnly: Boolean,

    var signUp: Boolean,

    override var signUpDeadline: Instant? = null,

    @field:Min(1, message = "Sign-up limit must be at least 1")
    var signUpLimit: Int? = null,

    @field:Valid
    var banner: EventBannerRequest? = null,

    @field:Valid
    var signUpForm: SurveyRequest? = null,

    /**
     * When true, all existing sign-ups for this event are deleted on save.
     * Defaults to false: form edits no longer cascade-delete sign-ups.
     */
    var removeExistingSignUps: Boolean? = false,

    var version: Long
) : HasSignUpDeadline
