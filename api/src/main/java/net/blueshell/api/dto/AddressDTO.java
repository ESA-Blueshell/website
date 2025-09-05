package net.blueshell.api.dto;

import lombok.Data;

@Data
public class AddressDTO {
    private Long id;
    private String address;
    private String houseNumber;
    private String postalCode;
    private String city;
    private String street;
    private String country;
    private String createdAt;
}