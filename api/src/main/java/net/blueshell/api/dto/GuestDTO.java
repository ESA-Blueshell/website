package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Guest")
public class GuestDTO extends BaseDTO {

    private Long id;

    private String name;

    private String discord;

    private String email;

    private Timestamp createdAt;

    private String accessToken;
}
