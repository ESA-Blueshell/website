package net.blueshell.api.dto.committee;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.dto.CommitteeMemberDTO;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "AdvancedCommittee")
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
