package net.blueshell.api.platform.integration.calendar

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.EventDateTime
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import jakarta.annotation.PostConstruct
import net.blueshell.api.event.persistence.Event
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.ZoneId

@Service
class CalendarService {
    @Value($$"${google.calendar.id}")
    private lateinit var calendarId: String

    @Value($$"${google.calendar.clientId}")
    private lateinit var clientId: String

    @Value($$"${google.calendar.clientEmail}")
    private lateinit var clientEmail: String

    @Value($$"${google.calendar.privateKeyPkcs8}")
    private lateinit var privateKeyPkcs8: String

    @Value($$"${google.calendar.privateKeyId}")
    private lateinit var privateKeyId: String

    private lateinit var service: Calendar
    private lateinit var htmlRenderer: HtmlRenderer
    private lateinit var htmlParser: Parser

    @PostConstruct
    fun init() {
        try {
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

            val credentials: GoogleCredentials = ServiceAccountCredentials
                .fromPkcs8(clientId, clientEmail, privateKeyPkcs8, privateKeyId, SCOPES)

            service = Calendar.Builder(
                httpTransport,
                GsonFactory.getDefaultInstance(),
                HttpCredentialsAdapter(credentials)
            ).setApplicationName(APPLICATION_NAME)
                .build()

            val options = MutableDataSet()
            options.set(
                Parser.EXTENSIONS, listOf(TablesExtension.create(), StrikethroughExtension.create())
            )
            htmlParser = Parser.builder(options).build()
            htmlRenderer = HtmlRenderer.builder(options).build()

            log.info("Initialized Google Calendar client for calendarId={}", calendarId)
        } catch (e: GeneralSecurityException) {
            log.error("Failed to initialize GoogleCalendarService", e)
            throw IllegalStateException("GoogleCalendarService initialization failed", e)
        } catch (e: IOException) {
            log.error("Failed to initialize GoogleCalendarService", e)
            throw IllegalStateException("GoogleCalendarService initialization failed", e)
        }
    }

    @Throws(IOException::class)
    fun add(event: Event) {
        var googleEvent = toGoogleEvent(event)
        try {
            googleEvent = service.events()
                .insert(calendarId, googleEvent)
                .execute()
            event.googleId = googleEvent.id
            log.info("Added a new event to the calendar at: {}", googleEvent.htmlLink)
        } catch (e: GoogleJsonResponseException) {
            log.error("Google Calendar API returned HTTP code {} during insert", e.statusCode, e)
            throw e
        }
    }

    @Throws(IOException::class)
    fun update(event: Event) {
        try {
            val googleEvent = toGoogleEvent(event)
            service.events()
                .update(calendarId, event.googleId, googleEvent)
                .execute()
            log.info("Updated event {} in calendar {}", event.googleId, calendarId)
        } catch (e: GoogleJsonResponseException) {
            log.error("Google Calendar API returned HTTP code {} during update", e.statusCode, e)
            throw e
        }
    }

    @Throws(IOException::class)
    fun remove(event: Event) {
        if (event.googleId == null) return
        try {
            service.events().delete(calendarId, event.googleId).execute()
            log.info("Removed event {} from calendar {}", event.googleId, calendarId)
            event.googleId = null
        } catch (e: GoogleJsonResponseException) {
            log.error("Google Calendar API returned HTTP code {} during removal", e.statusCode, e)
            throw e
        }
    }

    @Throws(IOException::class)
    fun sync(event: Event) {
        if (event.googleId != null) {
            if (event.approved) {
                update(event)
            } else {
                remove(event)
            }
        } else add(event)
    }

    private fun toGoogleEvent(event: Event): com.google.api.services.calendar.model.Event {
        val googleEvent = com.google.api.services.calendar.model.Event()
            .setSummary(event.title)
            .setLocation(event.location)

        var preProcessedHtml = htmlRenderer.render(htmlParser.parse(event.description ?: ""))
        preProcessedHtml = preProcessedHtml.replace("<p>", "").replace("</p>", "")
        googleEvent.description = preProcessedHtml

        val startDateTime = DateTime(event.startTime.atZone(ZONE).toEpochSecond() * 1000L)
        val endDateTime = DateTime(event.endTime.atZone(ZONE).toEpochSecond() * 1000L)

        val start = EventDateTime().setDateTime(endDateTime).setTimeZone(TZ_ID)
        val end = EventDateTime().setDateTime(startDateTime).setTimeZone(TZ_ID)

        googleEvent.start = start
        googleEvent.end = end
        return googleEvent
    }

    companion object {
        private val log = LoggerFactory.getLogger(CalendarService::class.java)
        private const val APPLICATION_NAME = "Blueshell Google Calendar API"
        private val SCOPES: List<String> = listOf(CalendarScopes.CALENDAR_EVENTS)
        private const val TZ_ID = "Europe/Amsterdam"
        private val ZONE: ZoneId = ZoneId.of(TZ_ID)
    }
}
