package net.blueshell.api.platform.integration.cohort.persistence

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Closed set of facts about a user that a [CohortRule] can pivot on.
 *
 * The `factKey` column on [CohortRule] is a free-form string whose
 * interpretation depends on this kind:
 *  - [ROLE]: a [net.blueshell.api.shared.enums.Role] enum name.
 *  - [COMMITTEE]: a committee id (decimal string).
 *  - [CONTRIBUTION_PAID]: a contribution-period id (decimal string).
 *  - [NEWSLETTER]: literally `"true"`; opt-out cohorts are simply
 *    represented by the absence of a rule.
 *
 * Adding a new kind only requires extending this enum, teaching
 * `UserFactCollector` how to derive the matching facts, and (in the
 * admin UI) how to enumerate possible `factKey` values.
 */
@Schema(enumAsRef = true)
enum class CohortFactKind {
    ROLE,
    COMMITTEE,
    CONTRIBUTION_PAID,
    NEWSLETTER,
}
