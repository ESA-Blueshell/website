package net.blueshell.api.dto;

import lombok.Data;

@Data
public class AddressDTO {
    private Long id;
    private String country;
    private String city;
    private String street;
    private String houseNumber;
    private String zipCode;
    private String createdAt;
}