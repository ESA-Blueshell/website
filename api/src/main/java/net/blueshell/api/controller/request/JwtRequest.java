package net.blueshell.api.controller.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.dto.BaseDTO;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
public class JwtRequest extends BaseDTO {
    @Serial
    private static final long serialVersionUID = 5926468583005150707L;
    private String username;
    private String password;

    public JwtRequest() {
    }

    public JwtRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
