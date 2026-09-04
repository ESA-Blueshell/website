package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.file.api.Image
import java.time.Instant

@Schema(name = "EventBannerResponse")
data class EventBannerResponse(
    @field:NotNull
    var eventId: Long,

    @field:NotNull
    var fileId: Long,

    /**
     * The banner itself: where it is served and the widths it is stored at.
     *
     * Beside [fileId] rather than instead of it. A reader that draws the banner wants this;
     * a save that replaces it names the file and its version, and still needs those.
     */
    var image: Image? = null,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
