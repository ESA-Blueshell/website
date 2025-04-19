package net.blueshell.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class EventFeedbackDTO extends BaseDTO {
    private Long id;
    private String feedback;
    private long eventId;
}
