package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

// The entry names are the wire values the frontend filters on, so they are
// schema and cannot be renamed to Kotlin's convention.
@Schema(enumAsRef = true)
@Suppress("EnumNaming")
enum class JobExecutionCategory {
    calendar,
    contact,
    cohort,
    discord,
    email,
    other,
}
