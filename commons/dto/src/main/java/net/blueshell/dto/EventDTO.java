package net.blueshell.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;


import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class EventDTO extends BaseDTO {
    private Long id;
    private Long committeeId;
    private SimpleCommitteeDTO committee;
    @NotBlank(message = "Event title cannot be empty.")
    @Size(max = 200, message = "Event title cannot exceed 200 characters.")
    private String title;
    @NotBlank(message = "Event description cannot be empty.")
    @JsonProperty("description")
    private String description;
    @JsonProperty("location")
    private String location;
    @NotBlank(message = "startTime is required (ISO-8601 string).")
    private String startTime;
    @NotBlank(message = "endTime is required (ISO-8601 string).")
    private String endTime;
    private String memberPrice;
    private String publicPrice;
    private boolean visible;
    private boolean membersOnly;
    private boolean signUp;
    private FileDTO banner;
    @Valid
    private List<FormQuestionDTO> signUpForm;
}
