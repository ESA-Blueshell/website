package net.blueshell.api.platform.integration.contact.adapter

import net.blueshell.api.shared.enums.ContactSystem

/**
 * Provider-neutral signal that the external contact at [externalContactId] no
 * longer exists in [system]. Thrown by [ContactAdapter] / [ContactListAdapter]
 * implementations so the orchestration layer can clear the stale pairing and
 * trigger a contact resync without depending on a provider-specific exception.
 */
class ExternalContactGoneException(
    val system: ContactSystem,
    val externalContactId: Long,
    cause: Throwable? = null,
) : ContactServiceException(
    "Contact $externalContactId does not exist in $system",
    cause,
)
