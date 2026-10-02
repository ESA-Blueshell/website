package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortType

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
    const val ACTIVISTS = "Activists"
    const val TEAMS = "Teams"
    const val BOARD = "Board"

    /** The folder a list for a cohort of [type] belongs in. */
    fun forType(type: CohortType): String =
        when (type) {
            CohortType.PERIOD_PAYERS -> CONTRIBUTION_PAID
            CohortType.PERIOD_MEMBERS -> MEMBERS
            CohortType.PERIOD_ACTIVE_MEMBERS -> ACTIVE_MEMBERS
            CohortType.COMMITTEE_MEMBERS -> COMMITTEES
            CohortType.NEWSLETTER_SUBSCRIBERS -> NEWSLETTER
            CohortType.ACTIVISTS -> ACTIVISTS
            CohortType.CURRENT_MEMBERS -> MEMBERS
            CohortType.TEAM_PLAYERS -> TEAMS
            CohortType.BOARD, CohortType.KANDI -> BOARD
        }
}
