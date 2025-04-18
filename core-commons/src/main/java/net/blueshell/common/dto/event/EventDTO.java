package net.blueshell.common.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.common.dto.BaseDTO;
import net.blueshell.common.dto.FileDTO;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class EventDTO extends BaseDTO {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("committeeId")
    private Long committeeId;

    @JsonProperty("committee")
    private CommitteeDTO committee;

    @NotBlank(message = "Event title cannot be empty.")
    @Size(max = 200, message = "Event title cannot exceed 200 characters.")
    @JsonProperty("title")
    private String title;

    @NotBlank(message = "Event description cannot be empty.")
    @JsonProperty("description")
    private String description;

    @JsonProperty("location")
    private String location;

    // Example: ensure it's a valid ISO-8601 datetime string.
    // If you wanted to parse to a LocalDateTime, you could do that in a custom mapper.
    @NotBlank(message = "startTime is required (ISO-8601 string).")
    @JsonProperty("startTime")
    private String startTime;

    @NotBlank(message = "endTime is required (ISO-8601 string).")
    @JsonProperty("endTime")
    private String endTime;

    @JsonProperty("memberPrice")
    private String memberPrice;

    @JsonProperty("publicPrice")
    private String publicPrice;

    @JsonProperty("visible")
    private boolean visible;

    @JsonProperty("membersOnly")
    private boolean membersOnly;

    @JsonProperty("signUp")
    private boolean signUp;

    private FileDTO banner;

    @JsonProperty("signUpForm")
    @Valid
    private List<SignUpFormDTO> signUpForm;
}
