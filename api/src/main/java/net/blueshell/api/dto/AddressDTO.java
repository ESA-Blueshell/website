package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.validation.address.ValidCountryCode;

import java.sql.Timestamp;
import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(name = "Address")
public class AddressDTO extends BaseDTO {
    private Long id;

    @NotNull
    @ValidCountryCode
    private String country;

    @NotNull
    private String city;

    @NotNull
    private String street;

    @NotNull
    private String houseNumber;

    @NotNull
    private String zipCode;

    private Instant createdAt;
}