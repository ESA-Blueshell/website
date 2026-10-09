package net.blueshell.api.platform.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.discord.DescriptionReader
import net.blueshell.api.shared.discord.DescriptionTree
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Schema(name = "DescriptionPreviewRequest", description = "A description being written, not yet saved")
data class DescriptionPreviewRequest(
    // Above the cap, so text past it is still read back and the cap's refusal can be shown on it.
    @field:Size(max = 16384, message = "A description to preview cannot exceed 16384 characters.")
    val text: String,
)

@RestController
@Tag(name = "Descriptions")
class DescriptionController {
    /** Any text read as a description is, without saving it, and its length as it would be stored. */
    @PostMapping("/descriptions/preview")
    @PreAuthorize("isAuthenticated()")
    fun previewDescription(
        @Valid @RequestBody request: DescriptionPreviewRequest,
    ): DescriptionTree = DescriptionReader.read(request.text)
}
