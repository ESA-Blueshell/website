package net.blueshell.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CommitteeMemberDTO extends BaseDTO {
    private Long id;
    private String role;
    private Long userId;
    private SimpleUserDTO user;
    private Long committeeId;
}
