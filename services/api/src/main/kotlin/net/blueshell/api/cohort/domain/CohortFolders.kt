package net.blueshell.api.cohort.domain

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
}
