package net.blueshell.common.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = false)
public class GuestDTO extends BaseDTO {

    private Long id;

    private String name;

    private String discord;

    private String email;

    private Timestamp createdAt;

    private String accessToken;
}
