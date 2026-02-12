package net.blueshell.api.domain.event.command

import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.filter.EventFilter
import net.blueshell.api.domain.event.web.dto.EventBannerRequest
import net.blueshell.api.domain.survey.web.dto.SurveyRequest
import java.time.Instant
import net.blueshell.api.shared.command.Command
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

data class CreateEventCommand(
    val committeeId: Long,
    val title: String,
    val description: String,
    val location: String?,
    val startTime: Instant,
    val endTime: Instant,
    val memberPrice: Double?,
    val publicPrice: Double?,
    val approved: Boolean,
    val membersOnly: Boolean,
    val signUp: Boolean,
    val banner: EventBannerRequest?,
    val signUpForm: SurveyRequest?
) : Command<Event>

data class UpdateEventCommand(
    val id: Long,
    val committeeId: Long,
    val title: String,
    val description: String,
    val location: String?,
    val startTime: Instant,
    val endTime: Instant,
    val memberPrice: Double?,
    val publicPrice: Double?,
    val approved: Boolean,
    val membersOnly: Boolean,
    val signUp: Boolean,
    val banner: EventBannerRequest?,
    val signUpForm: SurveyRequest?,
    val version: Long?
) : Command<Event>

data class ApproveEventCommand(
    val id: Long,
    val approved: Boolean
) : Command<Event>

data class FindEventByIdCommand(
    val id: Long
) : Command<Event>

data class FindEventsCommand(
    val pageable: Pageable,
    val filter: EventFilter
) : Command<Page<Event>>

data class DeleteEventByIdCommand(
    val eventId: Long
) : Command<Unit>
