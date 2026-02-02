package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import java.time.Instant


@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Schema(name = "Blog")
class BlogDTO : BaseDTO() {
    private val id: Long? = null
    private val url: String? = null

    @NotBlank
    private val title: @NotBlank String? = null

    @NotBlank
    private val html: @NotBlank String? = null
    private val publishedAt: Instant? = null
}

