package net.blueshell.api.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.dto.survey.SurveyDTO;
import net.blueshell.api.model.event.EventBanner;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "Event")
public class EventDTO extends BaseDTO {
    private Long id;
    @NotNull
    private Long committeeId;
    @NotBlank(message = "Event title cannot be empty.")
    @Size(max = 200, message = "Event title cannot exceed 200 characters.")
    private String title;
    @NotBlank(message = "Event description cannot be empty.")
    @JsonProperty("description")
    private String description;
    @JsonProperty("location")
    private String location;
    @NotNull
    private OffsetDateTime startTime;
    @NotNull
    private OffsetDateTime endTime;
    private Double memberPrice;
    private Double publicPrice;
    private boolean approved;
    private boolean membersOnly;
    private boolean signUp;
    private EventBannerDTO banner;
    private Long signUpCount;
    @Valid
    private SurveyDTO signUpForm;
}
