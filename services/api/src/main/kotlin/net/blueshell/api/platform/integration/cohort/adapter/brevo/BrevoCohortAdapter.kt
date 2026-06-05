package net.blueshell.api.platform.integration.cohort.adapter.brevo

import net.blueshell.api.platform.integration.cohort.port.out.CohortPort
import net.blueshell.api.platform.integration.cohort.port.out.MemberRef
import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.shared.enums.TargetSystem
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
        delegate.addToList(
            externalUserId.toBrevoId("externalUserId", "addMember"),
            externalCohortId.toBrevoId("externalCohortId", "addMember"),
        )
    }

    override fun removeMember(externalUserId: String, externalCohortId: String) {
        delegate.removeFromList(
            externalUserId.toBrevoId("externalUserId", "removeMember"),
            externalCohortId.toBrevoId("externalCohortId", "removeMember"),
        )
    }

    override fun deleteCohort(externalCohortId: String) {
        delegate.deleteList(externalCohortId.toBrevoId("externalCohortId", "deleteCohort"))
    }

    override fun listMembers(externalCohortId: String): List<MemberRef> =
        delegate.listMembers(externalCohortId.toBrevoId("externalCohortId", "listMembers"))
            .map { MemberRef(it.externalUserId.toString(), it.email) }
            // Never surface a blank external id: it would classify as an
            // INVALID ledger row downstream (see CohortMemberState).
            .filter { it.externalUserId.isNotBlank() }

    private fun String.toBrevoId(field: String, operation: String): Long =
        toLongOrNull() ?: throw InvalidExternalIdException(
            "Brevo $operation: $field \"$this\" is not a valid numeric id"
        )
}
