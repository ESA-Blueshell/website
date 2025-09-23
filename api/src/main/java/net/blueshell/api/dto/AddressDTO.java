package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import net.blueshell.api.validation.address.ValidCountryCode;

import java.sql.Timestamp;

@Data
@Schema(name = "Address")
public class AddressDTO {
    private Long id;

    @NotNull
    private Long userId;

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

    private Timestamp createdAt;
}