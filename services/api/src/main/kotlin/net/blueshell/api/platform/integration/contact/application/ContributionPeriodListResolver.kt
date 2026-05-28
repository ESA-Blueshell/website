package net.blueshell.api.platform.integration.contact.application

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Single source of truth for the contact list that backs a [ContributionPeriod]:
 * names it ("Contribution Paid YYYY - YYYY"), creates it in every registered
 * system on first call (via [ContactListService.findOrCreateList]), and links
 * it to the period. Both the per-(user, period) processing job and the nightly
 * reconciliation job go through this so the naming / folder / linking rule
 * stays in one place.
 */
@Service
class ContributionPeriodListResolver(
    private val contactListService: ContactListService,
    private val periods: ContributionPeriodService,
) {
    @Transactional
    fun resolve(period: ContributionPeriod): ContactList {
        period.contactListId?.let { return contactListService.findById(it) }
        val list = contactListService.findOrCreateList(listName(period), FOLDER_NAME)
        periods.updateContactListId(period.id!!, list.id!!)
        return list
    }

    companion object {
        private const val FOLDER_NAME = "contributionPeriods"

        fun listName(period: ContributionPeriod): String =
            "Contribution Paid ${period.startDate.year} - ${period.endDate.year}"
    }
}
