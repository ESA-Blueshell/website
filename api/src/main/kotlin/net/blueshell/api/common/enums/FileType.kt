package net.blueshell.api.common.enums

import io.swagger.v3.oas.annotations.media.Schema
import lombok.Getter

@Schema(enumAsRef = true)
@Getter
enum class FileType(directory: String) {
    DOCUMENT("documents"),
    PROFILE_PICTURE("profile-pictures"),
    EVENT_BANNER("event-banners"),
    EVENT_PICTURE("event-pictures"),
    SPONSOR_PICTURE("sponsor-pictures");

    private val directory: String?

    init {
        this.directory = directory
    }
}
