package net.blueshell.api.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "EventFeedback")
public class EventFeedbackDTO extends BaseDTO {
    private Long id;
    private String feedback;
    private long eventId;
}
