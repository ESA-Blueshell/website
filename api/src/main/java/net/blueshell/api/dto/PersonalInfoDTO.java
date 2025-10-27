package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.validation.user.ValidMobilePhoneNumber;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "PersonalInfo")
public class PersonalInfoDTO extends BaseDTO {
    @NotBlank
    private String discord;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @ValidMobilePhoneNumber
    private String phoneNumber;
}
