package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class FileType(val directory: String) {
    DOCUMENT("documents"),
    PROFILE_PICTURE("profile-pictures"),
    EVENT_BANNER("event-banners"),
    EVENT_PICTURE("event-pictures"),
    SPONSOR_PICTURE("sponsor-pictures");
}
