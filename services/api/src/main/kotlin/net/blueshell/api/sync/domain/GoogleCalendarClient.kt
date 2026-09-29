package net.blueshell.api.sync.domain

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.io.IOException
import java.time.Instant

/**
 * Low-level Google Calendar API client.
 *
 * This is NOT an Anti-Corruption Layer - it's a thin wrapper around Google's API.
 * The ACL is implemented in GoogleCalendarAdapter, which translates between
 * domain concepts and this client's Google-specific operations.
 */
@Component
@WhenCredentialsSet(Credentials.GOOGLE_CALENDAR_ID, Credentials.GOOGLE_CALENDAR_KEY)
class GoogleCalendarClient(
    @param:Value($$"${google.calendar.id}") private val calendarId: String,
    @Value($$"${google.calendar.serviceAccountJson}") serviceAccountJson: String,
    private val environment: Environment,
) {
    /** The client and the service account it signs in as, swapped as one. */
    private class Connection(
        val calendar: Calendar,
        val account: String,
    )

    // Running without a working service account must not block the api; a calendar call
    // says so instead (requireService).
    @Volatile private var connection: Connection? = build(serviceAccountJson)

    /** The service account calendar calls sign in as, or null while none builds. */
    internal val account: String? get() = connection?.account

    /**
     * Takes a service account rotated in Vault while the api runs (api ADR-033). One that is
     * blank or does not build keeps the client in use.
     */
    @EventListener(EnvironmentChangeEvent::class)
    fun onChange(event: EnvironmentChangeEvent) {
        if (Credentials.GOOGLE_CALENDAR_KEY !in event.keys) return
        val json = environment.getProperty(Credentials.GOOGLE_CALENDAR_KEY).orEmpty()
        if (json.isBlank()) {
            log.warn("The Google Calendar service account is blank; keeping the client in use")
            return
        }
        val rotated =
            build(json) ?: return log.warn("The rotated Google Calendar service account did not build; keeping the one in use")
        connection = rotated
        log.info("Google Calendar service account rotated to {}", rotated.account)
    }

    // Any failure here (invalid JSON, bad key material, TLS trust) leaves calendar sync off
    // rather than crashloop the api.
    private fun build(json: String): Connection? {
        if (json.isBlank()) {
            log.warn("google.calendar.serviceAccountJson is blank; calendar sync is disabled")
            return null
        }
        return try {
            val credentials: GoogleCredentials =
                GoogleCredentials
                    .fromStream(json.byteInputStream())
                    .createScoped(SCOPES)
            val calendar =
                Calendar
                    .Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        HttpCredentialsAdapter(credentials),
                    ).setApplicationName(APPLICATION_NAME)
                    .build()
            log.info("Initialized Google Calendar client for calendarId={}", calendarId)
            Connection(calendar, (credentials as? ServiceAccountCredentials)?.clientEmail.orEmpty())
        } catch (e: Exception) {
            log.warn("Google Calendar client init failed; calendar sync disabled: {}", e.toString())
            null
        }
    }

    private fun requireService(): Calendar =
        connection?.calendar ?: throw IllegalStateException(
            "Google Calendar client is not configured: seed google.calendar.serviceAccountJson in Vault; " +
                "the api takes it within one refresh interval.",
        )

    /**
     * Add an event to Google Calendar.
     * Returns the Google event ID and HTML link.
     */
    @Throws(IOException::class)
    fun addEvent(
        title: String,
        location: String?,
        description: String?,
        startTime: Instant,
        endTime: Instant,
    ): GoogleCalendarEventResult {
        val googleEvent = toGoogleEvent(title, location, description, startTime, endTime)
        try {
            val result =
                requireService()
                    .events()
                    .insert(calendarId, googleEvent)
                    .execute()
            log.info("Added event to Google Calendar: {}", result.htmlLink)
            return GoogleCalendarEventResult(
                eventId = result.id,
                htmlLink = result.htmlLink,
            )
        } catch (e: GoogleJsonResponseException) {
            log.error("Google Calendar API returned HTTP code {} during insert", e.statusCode, e)
            throw IOException("Failed to add event to Google Calendar", e)
        }
    }

    /**
     * Update an existing event in Google Calendar.
     */
    @Throws(IOException::class)
    fun updateEvent(
        googleEventId: String,
        title: String,
        location: String?,
        description: String?,
        startTime: Instant,
        endTime: Instant,
    ) {
        val googleEvent = toGoogleEvent(title, location, description, startTime, endTime)
        try {
            requireService()
                .events()
                .update(calendarId, googleEventId, googleEvent)
                .execute()
            log.info("Updated Google Calendar event: {}", googleEventId)
        } catch (e: GoogleJsonResponseException) {
            log.error("Google Calendar API returned HTTP code {} during update", e.statusCode, e)
            throw IOException("Failed to update event in Google Calendar", e)
        }
    }

    /**
     * Remove an event from Google Calendar.
     */
    @Throws(IOException::class)
    fun removeEvent(googleEventId: String) {
        try {
            requireService().events().delete(calendarId, googleEventId).execute()
            log.info("Removed event from Google Calendar: {}", googleEventId)
        } catch (e: GoogleJsonResponseException) {
            log.error("Google Calendar API returned HTTP code {} during removal", e.statusCode, e)
            throw IOException("Failed to remove event from Google Calendar", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GoogleCalendarClient::class.java)
        private const val APPLICATION_NAME = "Blueshell Google Calendar API"
        private val SCOPES: List<String> = listOf(CalendarScopes.CALENDAR_EVENTS)
    }
}

/**
 * Result of a Google Calendar operation.
 */
data class GoogleCalendarEventResult(
    val eventId: String,
    val htmlLink: String?,
)
