package net.blueshell.api.event.domain

import net.blueshell.api.shared.job.JobDefinition

object EventJobs {
    object EventSignup : JobDefinition<EventSignupPayload> {
        override val type: String = "email.event-signup"
        override val payloadType: Class<EventSignupPayload> = EventSignupPayload::class.java

        override fun dedupKey(payload: EventSignupPayload): String? = null
    }

    /**
     * Tells somebody a board member took their sign-up off an event. The sign-up is gone by the
     * time this runs, so the payload carries everything the email needs rather than an id.
     */
    object EventSignUpRemoved : JobDefinition<EventSignUpRemovedPayload> {
        override val type: String = "email.event-signup-removed"
        override val payloadType: Class<EventSignUpRemovedPayload> = EventSignUpRemovedPayload::class.java

        override fun dedupKey(payload: EventSignUpRemovedPayload): String? = null
    }

    data class EventSignupPayload(
        val eventSignUpId: Long,
        val guestAccessToken: String,
    )

    data class EventSignUpRemovedPayload(
        val recipientEmail: String,
        val recipientName: String,
        val eventTitle: String,
    )
}
