package net.blueshell.api.dto.committee;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "CommitteeMember")
public class CommitteeMemberDTO extends BaseDTO {
    private Long id;
    @NotBlank
    private Long userId;
    private Long committeeId;
    @NotBlank
    private String role;
}
