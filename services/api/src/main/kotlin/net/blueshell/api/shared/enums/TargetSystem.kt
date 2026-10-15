package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

/**
 * External system this app pushes aggregate state to. Persisted as a string in
 * `external_id_mapping.system`.
 */
@Schema(enumAsRef = true)
enum class TargetSystem { BREVO, GOOGLE_CALENDAR, DISCORD }
