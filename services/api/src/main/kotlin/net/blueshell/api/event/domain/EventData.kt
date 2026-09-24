package net.blueshell.api.event.domain

import net.blueshell.api.survey.api.SurveyData
import java.time.Instant

/**
 * The editable shape of an event, as the application layer accepts it.
 *
 * Create and update differ only in what surrounds this — an id, a version and
 * whether existing sign-ups are cleared — so the fields themselves are declared
 * once. They lived on two near-identical commands before.
 */
data class EventData(
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
    val signUpDeadline: Instant? = null,
    val signUpLimit: Int? = null,
    val banner: EventBannerData? = null,
    val signUpForm: SurveyData? = null,
    /** Null leaves the event's pinged roles as they are. */
    val pingedRoles: List<PingedRoleData>? = null,
    /** Null leaves the games the event names as they are. */
    val gameCodes: List<String>? = null,
)

/** A Discord role an event notifies: its ID and its name as last known. */
data class PingedRoleData(
    val id: String,
    val name: String,
)
