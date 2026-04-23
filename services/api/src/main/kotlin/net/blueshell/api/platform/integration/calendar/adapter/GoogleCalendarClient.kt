package net.blueshell.api.platform.integration.calendar.adapter

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.EventDateTime
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Instant
import java.time.ZoneId

/**
 * Low-level Google Calendar API client.
 *
 * This is NOT an Anti-Corruption Layer - it's a thin wrapper around Google's API.
 * The ACL is implemented in GoogleCalendarAdapter, which translates between
 * domain concepts and this client's Google-specific operations.
 */
@Component
@Profile("!test & !dev")
class GoogleCalendarClient {
    @Value($$"${google.calendar.id}")
    private lateinit var calendarId: String

    @Value($$"${google.calendar.serviceAccountJson}")
    private lateinit var serviceAccountJson: String

    private var service: Calendar? = null
    private lateinit var htmlRenderer: HtmlRenderer
    private lateinit var htmlParser: Parser

    @PostConstruct
    fun init() {
        // Markdown → HTML renderer is cheap and has no external deps;
        // set it up regardless so the no-credentials branch below can
        // still fail operations with clear errors rather than NPE.
        val options = MutableDataSet()
        options.set(
            Parser.EXTENSIONS, listOf(TablesExtension.create(), StrikethroughExtension.create())
        )
        htmlParser = Parser.builder(options).build()
        htmlRenderer = HtmlRenderer.builder(options).build()

        // Running the prod profile without Google Calendar creds must
        // not block api startup — other features (OIDC, API endpoints,
        // the frontend) should work. Operations that *actually* need
        // the client will throw below instead.
        if (serviceAccountJson.isBlank()) {
            log.warn(
                "google.calendar.serviceAccountJson is blank; calendar sync is disabled. " +
                "Seed secret/api.google-calendar-sa-json in Vault to enable it."
            )
            return
        }

        try {
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

            val credentials: GoogleCredentials = GoogleCredentials
                .fromStream(serviceAccountJson.byteInputStream())
                .createScoped(SCOPES)

            service = Calendar.Builder(
                httpTransport,
                GsonFactory.getDefaultInstance(),
                HttpCredentialsAdapter(credentials)
            ).setApplicationName(APPLICATION_NAME)
                .build()

            log.info("Initialized Google Calendar client for calendarId={}", calendarId)
        } catch (e: GeneralSecurityException) {
            log.error("Failed to initialize GoogleCalendarService", e)
            throw IllegalStateException("GoogleCalendarService initialization failed", e)
        } catch (e: IOException) {
            log.error("Failed to initialize GoogleCalendarService", e)
            throw IllegalStateException("GoogleCalendarService initialization failed", e)
        }
    }

    private fun requireService(): Calendar = service ?: throw IllegalStateException(
        "Google Calendar client is not configured — seed google-calendar-sa-json in Vault " +
        "and restart the api pod before invoking calendar operations."
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
        endTime: Instant
    ): GoogleCalendarEventResult {
        val googleEvent = toGoogleEvent(title, location, description, startTime, endTime)
        try {
            val result = requireService().events()
                .insert(calendarId, googleEvent)
                .execute()
            log.info("Added event to Google Calendar: {}", result.htmlLink)
            return GoogleCalendarEventResult(
                eventId = result.id,
                htmlLink = result.htmlLink
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
        endTime: Instant
    ) {
        val googleEvent = toGoogleEvent(title, location, description, startTime, endTime)
        try {
            requireService().events()
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

    private fun toGoogleEvent(
        title: String,
        location: String?,
        description: String?,
        startTime: Instant,
        endTime: Instant
    ): com.google.api.services.calendar.model.Event {
        val googleEvent = com.google.api.services.calendar.model.Event()
            .setSummary(title)
            .setLocation(location)

        // Convert Markdown to HTML and clean up
        description?.let {
            var preProcessedHtml = htmlRenderer.render(htmlParser.parse(it))
            preProcessedHtml = preProcessedHtml.replace("<p>", "").replace("</p>", "")
            googleEvent.description = preProcessedHtml
        }

        val startDateTime = DateTime(startTime.atZone(ZONE).toEpochSecond() * 1000L)
        val endDateTime = DateTime(endTime.atZone(ZONE).toEpochSecond() * 1000L)

        val start = EventDateTime().setDateTime(startDateTime).setTimeZone(TZ_ID)
        val end = EventDateTime().setDateTime(endDateTime).setTimeZone(TZ_ID)

        googleEvent.start = start
        googleEvent.end = end
        return googleEvent
    }

    companion object {
        private val log = LoggerFactory.getLogger(GoogleCalendarClient::class.java)
        private const val APPLICATION_NAME = "Blueshell Google Calendar API"
        private val SCOPES: List<String> = listOf(CalendarScopes.CALENDAR_EVENTS)
        private const val TZ_ID = "Europe/Amsterdam"
        private val ZONE: ZoneId = ZoneId.of(TZ_ID)
    }
}

/**
 * Result of a Google Calendar operation.
 */
data class GoogleCalendarEventResult(
    val eventId: String,
    val htmlLink: String?
)
