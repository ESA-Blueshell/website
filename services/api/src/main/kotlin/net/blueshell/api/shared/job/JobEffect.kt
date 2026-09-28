package net.blueshell.api.shared.job

import io.swagger.v3.oas.annotations.media.Schema

/** What a successful run did to the thing it keeps, for the jobs page to put in words. */
@Schema(enumAsRef = true)
enum class JobEffect {
    MADE,
    EDITED,
    UNCHANGED,
    REMOVED,
}
