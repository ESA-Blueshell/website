package net.blueshell.api.shared.enums

/**
 * Identifies an external system that holds a user's contact record.
 *
 * One value per registered [net.blueshell.api.platform.integration.contact.adapter.ContactAdapter]
 * implementation. Adding a new integration (Google Workspace, Discord, …) means
 * adding a new value here and wiring an adapter bean that reports it from
 * [net.blueshell.api.platform.integration.contact.adapter.ContactAdapter.system].
 *
 * The string [displayName] is human-facing (log messages, admin UI labels);
 * the enum constant itself is the stable identifier persisted on
 * [net.blueshell.api.platform.integration.contact.persistence.ContactExternalId.system].
 */
enum class ContactSystem(val displayName: String) {
    BREVO("Brevo"),
}
