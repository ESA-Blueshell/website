package net.blueshell.api.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Data
public class BrevoContactDTO {
    private String firstName;
    private String lastName;
    private String sms;
    private String whatsApp;
    private String extId;
    private Boolean newsletter;
    private Boolean incasso;
    private String memberType;
    private String surnamePrefix;
    private String country;
    private String city;
    private String street;
    private String email;

    // Utility method to convert DTO to Map, filtering nulls
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("FIRSTNAME", firstName);
        map.put("LASTNAME", lastName);
        map.put("SMS", sms);
        map.put("WHATSAPP", whatsApp);
        map.put("EXT_ID", extId);
        map.put("NEWSLETTER", newsletter);
        map.put("INCASSO", incasso);
        map.put("MEMBER_TYPE", memberType);
        map.put("SURNAME_PREFIX", surnamePrefix);
        map.put("COUNTRY", country);
        map.put("CITY", city);
        map.put("STREET", street);
        map.put("EMAIL", email);
        map.values().removeIf(Objects::isNull);
        return map;
    }
}