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
    ;

    /** The bucket the dashboard browses by: every per-period cohort collapses into PERIODS. */
    fun category(): CohortCategory =
        when (this) {
            COMMITTEE_MEMBERS -> CohortCategory.COMMITTEES
            PERIOD_PAYERS, PERIOD_MEMBERS, PERIOD_ACTIVE_MEMBERS -> CohortCategory.PERIODS
            NEWSLETTER_SUBSCRIBERS -> CohortCategory.MEMBERS
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
}
