package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "Address")
public class AddressDTO {
    private Long id;
    private String country;
    private String city;
    private String street;
    private String houseNumber;
    private String zipCode;
    private String createdAt;
}