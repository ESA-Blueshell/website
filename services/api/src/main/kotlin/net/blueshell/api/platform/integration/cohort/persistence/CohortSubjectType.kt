package net.blueshell.api.platform.integration.cohort.persistence

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Coarse classification of a [CohortSubject] used to group subjects on
 * the admin dashboard.
 *
 * Naming pattern is `<SCOPE>_<ROLE>`:
 *  - `<SCOPE>` is the dimension that fans out the subjects of this
 *    type — COMMITTEE (one subject per committee), PERIOD (one subject
 *    per contribution period) or NEWSLETTER (one global subject).
 *  - `<ROLE>` is the noun describing the people in the subject —
 *    MEMBERS, PAYERS, SUBSCRIBERS — kept plural because each subject is
 *    a group of users.
 *
 *  - [COMMITTEE_MEMBERS]: members of one committee. Rule pivots on
 *    `COMMITTEE`.
 *  - [PERIOD_PAYERS]: members who paid the contribution for one period.
 *    Rule pivots on `CONTRIBUTION_PAID`.
 *  - [PERIOD_MEMBERS]: members who held a Membership during one period.
 *    Rule pivots on `MEMBER_IN_PERIOD`.
 *  - [PERIOD_ACTIVE_MEMBERS]: members active in a committee (or in the
 *    future an esports team) during one period. Rule pivots on
 *    `ACTIVE_IN_PERIOD`.
 *  - [NEWSLETTER_SUBSCRIBERS]: the single newsletter opt-in subject.
 *    Rule pivots on `NEWSLETTER`.
 *
 * Every type here is produced by a definition in code. There is no operator-created type: one
 * existed, nothing could create it, and no row ever used it.
 */
@Schema(enumAsRef = true)
enum class CohortSubjectType {
    COMMITTEE_MEMBERS,
    PERIOD_PAYERS,
    PERIOD_MEMBERS,
    PERIOD_ACTIVE_MEMBERS,
    NEWSLETTER_SUBSCRIBERS,
    ;

    /**
     * Operator-facing grouping shown as the top-level browse page on
     * the admin dashboard. The PERIODS bucket covers every per-period
     * subject (paid, member, active) since they all share the same
     * "scoped by contribution period" mental shape; MEMBERS holds the
     * non-period member-status axes (today: just newsletter).
     */
    fun category(): CohortSubjectCategory = when (this) {
        COMMITTEE_MEMBERS -> CohortSubjectCategory.COMMITTEES
        PERIOD_PAYERS, PERIOD_MEMBERS, PERIOD_ACTIVE_MEMBERS -> CohortSubjectCategory.PERIODS
        NEWSLETTER_SUBSCRIBERS -> CohortSubjectCategory.MEMBERS
    }
}

/**
 * Coarse buckets the admin UI uses to group subjects. Distinct from
 * [CohortSubjectType] (which is precise — "active in period 25-26" vs
 * "paid contribution for period 25-26") so the engine keeps the
 * granular signal while operators see a flatter taxonomy matching their
 * mental model.
 */
@Schema(enumAsRef = true)
enum class CohortSubjectCategory {
    COMMITTEES,
    PERIODS,
    MEMBERS,
}
