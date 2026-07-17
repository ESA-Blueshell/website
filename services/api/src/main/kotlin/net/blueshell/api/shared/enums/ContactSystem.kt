package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Legacy enum tagging rows of the `contact_external_ids` table.
 *
 * New code should use `TargetSystem` in the sync package; this enum will be
 * dropped once `contact_external_ids` is removed in favour of the unified
 * `external_id_mapping` table.
 */
@Schema(enumAsRef = true)
enum class ContactSystem { BREVO }
