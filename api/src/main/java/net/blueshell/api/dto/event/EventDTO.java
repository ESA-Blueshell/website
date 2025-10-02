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
import net.blueshell.api.dto.survey.QuestionDTO;
import net.blueshell.api.dto.survey.SurveyDTO;

import java.time.OffsetDateTime;
import java.util.List;

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
    private boolean visible;
    private boolean membersOnly;
    private boolean signUp;
    private FileDTO banner;
    @Valid
    private SurveyDTO signUpForm;
}
