package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class JobExecutionCategory {
    calendar,
    contact,
    cohort,
    email,
    other,
}
