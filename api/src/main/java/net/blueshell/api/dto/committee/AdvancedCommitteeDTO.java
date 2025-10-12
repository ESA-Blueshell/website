package net.blueshell.api.dto.committee;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "AdvancedCommittee")
public class AdvancedCommitteeDTO extends BaseDTO {

    @JsonProperty("id")
    private Long id;

    @NotBlank(message = "Committee name cannot be blank.")
    @Size(max = 255, message = "Committee name cannot exceed 255 characters.")
    @JsonProperty("name")
    private String name;

    @NotBlank(message = "Committee description cannot be empty.")
    @Size(max = 4095, message = "Committee description cannot exceed 4095 characters.")
    private String description;

    @JsonProperty("members")
    @NotEmpty
    private List<CommitteeMemberDTO> members;
}
