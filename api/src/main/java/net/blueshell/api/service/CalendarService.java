package net.blueshell.api.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.model.event.Event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class CalendarService {

    private static final String APPLICATION_NAME = "Blueshell Google Calendar API";
    private static final List<String> SCOPES = List.of(CalendarScopes.CALENDAR_EVENTS);
    private static final String TZ_ID = "Europe/Amsterdam";
    private static final ZoneId ZONE = ZoneId.of(TZ_ID);

    @Value("${google.calendar.id}")
    private String calendarId;

    @Value("${google.calendar.clientId}")
    private String clientId;

    @Value("${google.calendar.clientEmail}")
    private String clientEmail;

    @Value("${google.calendar.privateKeyPkcs8}")
    private String privateKeyPkcs8;

    @Value("${google.calendar.privateKeyId}")
    private String privateKeyId;

    private Calendar service;
    private HtmlRenderer htmlRenderer;
    private Parser htmlParser;

    @PostConstruct
    void init() {
        try {
            final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            GoogleCredentials credentials = ServiceAccountCredentials
                    .fromPkcs8(clientId, clientEmail, privateKeyPkcs8, privateKeyId, SCOPES);

            service = new Calendar.Builder(
                    httpTransport,
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials)
            )
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            MutableDataSet options = new MutableDataSet();
            options.set(Parser.EXTENSIONS, Arrays.asList(
                    TablesExtension.create(),
                    StrikethroughExtension.create()
            ));
            htmlParser = Parser.builder(options).build();
            htmlRenderer = HtmlRenderer.builder(options).build();

            log.info("Initialized Google Calendar client for calendarId={}", calendarId);
        } catch (GeneralSecurityException | IOException e) {
            log.error("Failed to initialize GoogleCalendarService", e);
            throw new IllegalStateException("GoogleCalendarService initialization failed", e);
        }
    }

    public void add(Event event) throws IOException {
        var googleEvent = toGoogleEvent(event);
        try {
            googleEvent = service.events()
                    .insert(calendarId, googleEvent)
                    .execute();
            event.setGoogleId(googleEvent.getId());
            log.info("Added a new event to the calendar at: {}", googleEvent.getHtmlLink());
        } catch (GoogleJsonResponseException e) {
            log.error("Google Calendar API returned HTTP code {} during insert", e.getStatusCode(), e);
            throw e;
        }
    }

    public void update(Event event) throws IOException {
        try {
            var googleEvent = toGoogleEvent(event);
            service.events()
                    .update(calendarId, event.getGoogleId(), googleEvent)
                    .execute();
            log.info("Updated event {} in calendar {}", event.getGoogleId(), calendarId);
        } catch (GoogleJsonResponseException e) {
            log.error("Google Calendar API returned HTTP code {} during update", e.getStatusCode(), e);
            throw e;
        }
    }

    public void remove(Event event) throws IOException {
        if (event.getGoogleId() == null) return;
        try {
            service.events().delete(calendarId, event.getGoogleId()).execute();
            log.info("Removed event {} from calendar {}", event.getGoogleId(), calendarId);
            event.setGoogleId(null);
        } catch (GoogleJsonResponseException e) {
            log.error("Google Calendar API returned HTTP code {} during removal", e.getStatusCode(), e);
            throw e;
        }
    }

    public void sync(Event event) throws IOException {
        if (event.getGoogleId() != null) update(event);
        else add(event);
    }

    private com.google.api.services.calendar.model.Event toGoogleEvent(Event event) {
        var googleEvent = new com.google.api.services.calendar.model.Event()
                .setSummary(event.getTitle())
                .setLocation(event.getLocation());

        String preProcessedHtml = htmlRenderer.render(htmlParser.parse(event.getDescription()));
        preProcessedHtml = preProcessedHtml.replace("<p>", "").replace("</p>", "");
        googleEvent.setDescription(preProcessedHtml);

        DateTime startDateTime = new DateTime(event.getStartTime().atZone(ZONE).toEpochSecond() * 1000L);
        DateTime endDateTime = new DateTime(event.getEndTime().atZone(ZONE).toEpochSecond() * 1000L);

        EventDateTime start = new EventDateTime().setDateTime(startDateTime).setTimeZone(TZ_ID);
        EventDateTime end = new EventDateTime().setDateTime(endDateTime).setTimeZone(TZ_ID);

        googleEvent.setStart(start);
        googleEvent.setEnd(end);
        return googleEvent;
    }
}
