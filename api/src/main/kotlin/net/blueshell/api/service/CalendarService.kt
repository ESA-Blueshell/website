package net.blueshell.api.service

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
import com.vladsch.flexmark.util.misc.Extension
import jakarta.annotation.PostConstruct
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.model.event.Event
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.ZoneId
import java.util.*
import java.util.List
import kotlin.collections.MutableCollection
import kotlin.collections.MutableList

@Slf4j
@Service
class CalendarService {
    @Value("\${google.calendar.id}")
    private val calendarId: String? = null

    @Value("\${google.calendar.clientId}")
    private val clientId: String? = null

    @Value("\${google.calendar.clientEmail}")
    private val clientEmail: String? = null

    @Value("\${google.calendar.privateKeyPkcs8}")
    private val privateKeyPkcs8: String? = null

    @Value("\${google.calendar.privateKeyId}")
    private val privateKeyId: String? = null

    private var service: Calendar? = null
    private var htmlRenderer: HtmlRenderer? = null
    private var htmlParser: Parser? = null

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
            )
                .setApplicationName(APPLICATION_NAME)
                .build()

            val options = MutableDataSet()
            options.set<MutableCollection<Extension?>?>(
                Parser.EXTENSIONS, Arrays.asList<Extension?>(
                    TablesExtension.create(),
                    StrikethroughExtension.create()
                )
            )
            htmlParser = Parser.builder(options).build()
            htmlRenderer = HtmlRenderer.builder(options).build()

            CalendarService.log.info("Initialized Google Calendar client for calendarId={}", calendarId)
        } catch (e: GeneralSecurityException) {
            CalendarService.log.error("Failed to initialize GoogleCalendarService", e)
            throw IllegalStateException("GoogleCalendarService initialization failed", e)
        } catch (e: IOException) {
            CalendarService.log.error("Failed to initialize GoogleCalendarService", e)
            throw IllegalStateException("GoogleCalendarService initialization failed", e)
        }
    }

    @Throws(IOException::class)
    fun add(event: Event) {
        var googleEvent = toGoogleEvent(event)
        try {
            googleEvent = service!!.events()
                .insert(calendarId, googleEvent)
                .execute()
            event.setGoogleId(googleEvent.id)
            CalendarService.log.info("Added a new event to the calendar at: {}", googleEvent.htmlLink)
        } catch (e: GoogleJsonResponseException) {
            CalendarService.log.error("Google Calendar API returned HTTP code {} during insert", e.statusCode, e)
            throw e
        }
    }

    @Throws(IOException::class)
    fun update(event: Event) {
        try {
            val googleEvent = toGoogleEvent(event)
            service!!.events()
                .update(calendarId, event.getGoogleId(), googleEvent)
                .execute()
            CalendarService.log.info("Updated event {} in calendar {}", event.getGoogleId(), calendarId)
        } catch (e: GoogleJsonResponseException) {
            CalendarService.log.error("Google Calendar API returned HTTP code {} during update", e.statusCode, e)
            throw e
        }
    }

    @Throws(IOException::class)
    fun remove(event: Event) {
        if (event.getGoogleId() == null) return
        try {
            service!!.events().delete(calendarId, event.getGoogleId()).execute()
            CalendarService.log.info("Removed event {} from calendar {}", event.getGoogleId(), calendarId)
            event.setGoogleId(null)
        } catch (e: GoogleJsonResponseException) {
            CalendarService.log.error("Google Calendar API returned HTTP code {} during removal", e.statusCode, e)
            throw e
        }
    }

    @Throws(IOException::class)
    fun sync(event: Event) {
        if (event.getGoogleId() != null) update(event)
        else add(event)
    }

    private fun toGoogleEvent(event: Event): com.google.api.services.calendar.model.Event {
        val googleEvent = com.google.api.services.calendar.model.Event()
            .setSummary(event.getTitle())
            .setLocation(event.getLocation())

        var preProcessedHtml = htmlRenderer!!.render(htmlParser!!.parse(event.getDescription()))
        preProcessedHtml = preProcessedHtml.replace("<p>", "").replace("</p>", "")
        googleEvent.description = preProcessedHtml

        val startDateTime = DateTime(event.getStartTime().atZone(ZONE).toEpochSecond() * 1000L)
        val endDateTime = DateTime(event.getEndTime().atZone(ZONE).toEpochSecond() * 1000L)

        val start = EventDateTime().setDateTime(startDateTime).setTimeZone(TZ_ID)
        val end = EventDateTime().setDateTime(endDateTime).setTimeZone(TZ_ID)

        googleEvent.start = start
        googleEvent.end = end
        return googleEvent
    }

    companion object {
        private const val APPLICATION_NAME = "Blueshell Google Calendar API"
        private val SCOPES: MutableList<String?> = List.of<String?>(CalendarScopes.CALENDAR_EVENTS)
        private const val TZ_ID = "Europe/Amsterdam"
        private val ZONE: ZoneId = ZoneId.of(TZ_ID)
    }
}
