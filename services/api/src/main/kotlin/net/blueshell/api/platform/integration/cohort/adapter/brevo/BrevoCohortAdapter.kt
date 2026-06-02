package net.blueshell.api.platform.integration.cohort.adapter.brevo

import net.blueshell.api.platform.integration.cohort.adapter.CohortAdapter
import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.enums.ContactSystem
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/**
 * Brevo's [CohortAdapter] implementation. Delegates to the existing
 * [ContactListAdapter] beans for the actual Brevo HTTP calls so we
 * inherit their recovery semantics (idempotent add, contact-gone
 * disambiguation, ...) without duplicating client wiring.
 *
 * The legacy `ContactListAdapter` interface continues to exist
 * alongside this adapter while the engine cutover happens; PR
 * D2a-iii-b deletes the legacy interface and inlines the implementation
 * here. Two-phase rollout keeps each diff reviewable.
 *
 * Lives under `cohort/adapter/brevo/` to mirror the existing
 * `contact/adapter/brevo/` structure and satisfy the platform
 * adapter-package convention (ADR-022).
 */
@Service
@Profile("!test & !dev")
class BrevoCohortAdapter(
    contactListAdapters: List<ContactListAdapter>,
) : CohortAdapter {

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
}
