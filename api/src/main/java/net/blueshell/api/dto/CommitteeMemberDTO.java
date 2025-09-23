package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.dto.user.SimpleUserDTO;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "CommitteeMember")
public class CommitteeMemberDTO extends BaseDTO {
    private Long id;
    @NotBlank
    private Long userId;
    @NotNull
    private Long committeeId;
    @NotBlank
    private String role;
    private SimpleUserDTO user;
}
