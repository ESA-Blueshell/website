package net.blueshell.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class AdvancedCommitteeDTO extends BaseDTO {

    @JsonProperty("id")
    private Long id;

    @NotBlank(message = "Committee name cannot be blank.")
    @Size(max = 100, message = "Committee name cannot exceed 100 characters.")
    @JsonProperty("name")
    private String name;

    @Size(max = 2000, message = "Committee description cannot exceed 500 characters.")
    @JsonProperty("description")
    private String description;

    @JsonProperty("members")
    private List<CommitteeMemberDTO> members;
}
