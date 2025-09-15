package net.blueshell.api.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.blueshell.api.dto.BaseDTO;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class JwtRequest extends BaseDTO {
    @Serial
    private static final long serialVersionUID = 5926468583005150707L;
    private String username;
    private String password;

    public JwtRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
