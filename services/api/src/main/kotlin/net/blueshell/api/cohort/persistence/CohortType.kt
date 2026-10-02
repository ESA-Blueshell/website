package net.blueshell.api.cohort.persistence

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Classification of a [Cohort], named `<SCOPE>_<ROLE>`: the dimension that fans the
 * cohorts out, then the people in them. Every type is produced by a definition in code.
 */
@Schema(enumAsRef = true)
enum class CohortType {
    /** Members of one committee. Pivots on `COMMITTEE`. */
    COMMITTEE_MEMBERS,

    /** Members who paid the contribution for one period. Pivots on `CONTRIBUTION_PAID`. */
    PERIOD_PAYERS,

    /** Members who held a membership during one period. Pivots on `MEMBER_IN_PERIOD`. */
    PERIOD_MEMBERS,

    /** Members active in a committee during one period. Pivots on `ACTIVE_IN_PERIOD`. */
    PERIOD_ACTIVE_MEMBERS,

    /** The single newsletter opt-in cohort. Pivots on `NEWSLETTER`. */
    NEWSLETTER_SUBSCRIBERS,

    /** Everybody holding a committee or board seat today. */
    ACTIVISTS,

    /** Everybody with a membership today. */
    CURRENT_MEMBERS,

    /** Everybody on one team's line-up in the season fielded now. Pivots on `TEAM`. */
    TEAM_PLAYERS,

    /** The board in office today. */
    BOARD,

    /** Kandi: the next board, from when it is named until the day it takes office. */
    KANDI,
    ;

    /** Whether registering a cohort of this type makes it a Brevo list; the others exist for Discord and Workspace. */
    val listedOnBrevo: Boolean get() = this != ACTIVISTS && this != CURRENT_MEMBERS && this != TEAM_PLAYERS

    /** The bucket the dashboard browses by: every per-period cohort collapses into PERIODS. */
    fun category(): CohortCategory =
        when (this) {
            COMMITTEE_MEMBERS -> CohortCategory.COMMITTEES
            PERIOD_PAYERS, PERIOD_MEMBERS, PERIOD_ACTIVE_MEMBERS -> CohortCategory.PERIODS
            NEWSLETTER_SUBSCRIBERS, ACTIVISTS, CURRENT_MEMBERS, BOARD, KANDI -> CohortCategory.MEMBERS
            TEAM_PLAYERS -> CohortCategory.TEAMS
        }
}

/**
 * Coarse buckets the admin UI groups by, flatter than [CohortType]: "active in a period"
 * and "paid for a period" are one bucket here, while the engine keeps the granular type.
 */
@Schema(enumAsRef = true)
enum class CohortCategory {
    COMMITTEES,
    PERIODS,
    MEMBERS,
    TEAMS,
}
