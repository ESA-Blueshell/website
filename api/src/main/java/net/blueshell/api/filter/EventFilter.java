package net.blueshell.api.filter;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class EventFilter {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime to;

    // Optional toggles
    private Boolean visible;        // if null, leave unspecified; if true/false, enforce
    private Boolean membersOnly;    // if null, unspecified
    private Boolean publicOnly;     // convenience flag (membersOnly == false)

    // Example extra filters you can grow over time
    private Long committeeId;
    private String titleContains;
}