package net.blueshell.api.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import lombok.Data
import lombok.EqualsAndHashCode
import lombok.NoArgsConstructor
import net.blueshell.api.base.BaseDTO
import java.io.Serial

@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Data
@NoArgsConstructor
@Schema(name = "JwtRequest")
class JwtRequest(private var username: String?, private var password: String?) : BaseDTO() {
    companion object {
        @Serial
        private const val serialVersionUID = 5926468583005150707L
    }
}
