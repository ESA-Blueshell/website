package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortSubjectType

/**
 * The Brevo folder a new list for each cohort type goes into. Brevo folders do not nest, so each
 * type has one; a folder that is missing is created by this name.
 */
object CohortFolders {
    const val CONTRIBUTION_PAID = "Contribution paid"
    const val MEMBERS = "Members"
    const val ACTIVE_MEMBERS = "Active members"
    const val COMMITTEES = "Committees"
    const val NEWSLETTER = "Newsletter"

    /** The folder a list for a cohort of [type] belongs in. */
    fun forType(type: CohortSubjectType): String =
        when (type) {
            CohortSubjectType.PERIOD_PAYERS -> CONTRIBUTION_PAID
            CohortSubjectType.PERIOD_MEMBERS -> MEMBERS
            CohortSubjectType.PERIOD_ACTIVE_MEMBERS -> ACTIVE_MEMBERS
            CohortSubjectType.COMMITTEE_MEMBERS -> COMMITTEES
            CohortSubjectType.NEWSLETTER_SUBSCRIBERS -> NEWSLETTER
        }
}
