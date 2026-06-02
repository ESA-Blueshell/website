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
 *    True when the user has any contribution row for the period.
 *  - [MEMBER_IN_PERIOD]: a contribution-period id (decimal string).
 *    True when the user held `Role.MEMBER` during the period — the
 *    membership-status counterpart of `CONTRIBUTION_PAID`, which only
 *    proves the contribution was paid. Historical periods will be
 *    backfilled once user-role history is queryable; today the fact
 *    is emitted for the current period only.
 *  - [NEWSLETTER]: literally `"true"`; opt-out cohorts are simply
 *    represented by the absence of a rule.
 *  - [ACTIVE_IN_PERIOD]: a contribution-period id (decimal string).
 *    True when the user has any committee membership that overlaps
 *    the period (esports-team activity will join this kind once teams
 *    grow a persistence layer).
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
    MEMBER_IN_PERIOD,
    NEWSLETTER,
    ACTIVE_IN_PERIOD,
}
