package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

/**
 * External system this app pushes aggregate state to. Persisted as a string in
 * `external_id_mapping.system`.
 *
 * Adding a new target (e.g. `GOOGLE_WORKSPACE`, `DISCORD`) is one new enum
 * value here plus one new `SyncTarget` implementation; the fan-out driver and
 * the mapping table need no further change.
 */
@Schema(enumAsRef = true)
enum class TargetSystem { BREVO, GOOGLE_CALENDAR }
