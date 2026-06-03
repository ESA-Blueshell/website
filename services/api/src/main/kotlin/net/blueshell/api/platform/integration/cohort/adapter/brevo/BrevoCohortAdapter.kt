package net.blueshell.api.platform.integration.cohort.adapter.brevo

import net.blueshell.api.platform.integration.cohort.port.out.CohortPort
import net.blueshell.api.platform.integration.cohort.port.out.MemberRef
import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.enums.ContactSystem
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/**
 * Brevo's [CohortPort] implementation. Delegates to the existing
 * Brevo [ContactListAdapter] bean for the actual HTTP calls so we
 * inherit its recovery semantics (idempotent add, contact-gone
 * disambiguation, ...) without duplicating client wiring.
 */
@Service
@Profile("!test & !dev")
class BrevoCohortAdapter(
    contactListAdapters: List<ContactListAdapter>,
) : CohortPort {

    private val delegate: ContactListAdapter = contactListAdapters.single { it.system == ContactSystem.BREVO }

    override val system: TargetSystem = TargetSystem.BREVO

    override fun createCohort(label: String, hint: String?): String =
        delegate.createList(label, hint).toString()

    override fun addMember(externalUserId: String, externalCohortId: String) {
        delegate.addToList(externalUserId.toLong(), externalCohortId.toLong())
    }

    override fun removeMember(externalUserId: String, externalCohortId: String) {
        delegate.removeFromList(externalUserId.toLong(), externalCohortId.toLong())
    }

    override fun deleteCohort(externalCohortId: String) {
        delegate.deleteList(externalCohortId.toLong())
    }

    override fun listMembers(externalCohortId: String): List<MemberRef> =
        delegate.listMembers(externalCohortId.toLong())
            .map { MemberRef(it.externalUserId.toString(), it.email) }
}
