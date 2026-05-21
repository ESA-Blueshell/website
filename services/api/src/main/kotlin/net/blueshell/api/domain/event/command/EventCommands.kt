package net.blueshell.api.domain.event.command

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.application.query.EventQuery
import net.blueshell.api.domain.survey.command.SurveyData
import java.time.Instant
import net.blueshell.api.shared.command.Command
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

data class CreateEventCommand(
    @field:NotNull(message = "Committee ID is required")
    var committeeId: Long,

    @field:NotBlank(message = "Event title is required")
    @field:Size(min = 1, max = 200, message = "Title must be 1-200 characters")
    var title: String,

    @field:NotBlank(message = "Event description is required")
    @field:Size(min = 1, max = 5000, message = "Description must be 1-5000 characters")
    var description: String,

    val location: String?,

    @field:NotNull(message = "Start time is required")
    var startTime: Instant,

    @field:NotNull(message = "End time is required")
    var endTime: Instant,

    val memberPrice: Double?,
    val publicPrice: Double?,

    @field:NotNull(message = "Approved status is required")
    var approved: Boolean,

    @field:NotNull(message = "Members only status is required")
    var membersOnly: Boolean,

    @field:NotNull(message = "Sign up status is required")
    var signUp: Boolean,

    val signUpDeadline: Instant? = null,

    @field:Min(1, message = "Sign-up limit must be at least 1")
    val signUpLimit: Int? = null,

    @field:Valid
    val banner: EventBannerData?,

    @field:Valid
    val signUpForm: SurveyData?
) : Command<Event>

data class UpdateEventCommand(
    @field:NotNull(message = "Event ID is required")
    var id: Long,

    @field:NotNull(message = "Committee ID is required")
    var committeeId: Long,

    @field:NotBlank(message = "Event title is required")
    @field:Size(min = 1, max = 200, message = "Title must be 1-200 characters")
    var title: String,

    @field:NotBlank(message = "Event description is required")
    @field:Size(min = 1, max = 5000, message = "Description must be 1-5000 characters")
    var description: String,

    val location: String?,

    @field:NotNull(message = "Start time is required")
    var startTime: Instant,

    @field:NotNull(message = "End time is required")
    var endTime: Instant,

    val memberPrice: Double?,
    val publicPrice: Double?,

    @field:NotNull(message = "Approved status is required")
    var approved: Boolean,

    @field:NotNull(message = "Members only status is required")
    var membersOnly: Boolean,

    @field:NotNull(message = "Sign up status is required")
    var signUp: Boolean,

    val signUpDeadline: Instant? = null,

    @field:Min(1, message = "Sign-up limit must be at least 1")
    val signUpLimit: Int? = null,

    @field:Valid
    val banner: EventBannerData?,

    @field:Valid
    val signUpForm: SurveyData?,

    val removeExistingSignUps: Boolean = false,

    @field:NotNull(message = "Version is required for optimistic locking")
    val version: Long
) : Command<Event>

data class ApproveEventCommand(
    @field:NotNull(message = "Event ID is required")
    var id: Long,

    @field:NotNull(message = "Approved status is required")
    var approved: Boolean
) : Command<Event>

data class FindEventByIdCommand(
    @field:NotNull(message = "Event ID is required")
    var id: Long
) : Command<Event>

data class FindEventsCommand(
    @field:NotNull(message = "Pageable is required")
    var pageable: Pageable,

    @field:NotNull(message = "Filter is required")
    var filter: EventQuery
) : Command<Page<Event>>

data class DeleteEventByIdCommand(
    @field:NotNull(message = "Event ID is required")
    var eventId: Long
) : Command<Unit>
