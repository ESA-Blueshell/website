package net.blueshell.api.sync.domain

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import jakarta.annotation.PostConstruct
import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.io.IOException
import java.security.GeneralSecurityException
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
class GoogleCalendarClient {
    @Value($$"${google.calendar.id}")
    private lateinit var calendarId: String

    @Value($$"${google.calendar.serviceAccountJson}")
    private lateinit var serviceAccountJson: String

    @Autowired
    private lateinit var environment: Environment

    @Volatile private var service: Calendar? = null

    @PostConstruct
    fun init() {
        // Running the prod profile without Google Calendar creds must
        // not block api startup — other features (OIDC, API endpoints,
        // the frontend) should work. Operations that *actually* need
        // the client will throw below instead.
        if (serviceAccountJson.isBlank()) {
            log.warn(
                "google.calendar.serviceAccountJson is blank; calendar sync is disabled. " +
                    "Seed google.calendar.serviceAccountJson in Vault secret/api to enable it.",
            )
            return
        }
        service = build(serviceAccountJson)
    }

    /**
     * Takes a service account rotated in Vault while the api runs (api ADR-033). One that does
     * not build keeps the client in use.
     */
    @EventListener(EnvironmentChangeEvent::class)
    fun onChange(event: EnvironmentChangeEvent) {
        if (Credentials.GOOGLE_CALENDAR_KEY !in event.keys) return
        val json = environment.getProperty(Credentials.GOOGLE_CALENDAR_KEY).orEmpty()
        val rotated = if (json.isBlank()) null else build(json)
        if (rotated == null) {
            log.warn("Kept the Google Calendar client in use; the rotated service account did not build")
            return
        }
        service = rotated
        log.info("Google Calendar service account rotated")
    }

    // Any failure here (invalid JSON, bad key material, TLS trust) leaves calendar sync off
    // rather than crashloop the api; requireService() says so if a calendar call is made.
    private fun build(json: String): Calendar? =
        try {
            val credentials: GoogleCredentials =
                GoogleCredentials
                    .fromStream(json.byteInputStream())
                    .createScoped(SCOPES)
            Calendar
                .Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    HttpCredentialsAdapter(credentials),
                ).setApplicationName(APPLICATION_NAME)
                .build()
                .also { log.info("Initialized Google Calendar client for calendarId={}", calendarId) }
        } catch (e: GeneralSecurityException) {
            log.warn("Google Calendar client init failed (security); calendar sync disabled: {}", e.message)
            null
        } catch (e: IOException) {
            // MalformedJsonException extends IOException: a placeholder or half-seeded SA JSON.
            log.warn("Google Calendar client init failed (invalid JSON / I/O); calendar sync disabled: {}", e.message)
            null
        } catch (e: RuntimeException) {
            log.warn("Google Calendar client init failed; calendar sync disabled: {}", e.message)
            null
        }

    private fun requireService(): Calendar =
        service ?: throw IllegalStateException(
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
