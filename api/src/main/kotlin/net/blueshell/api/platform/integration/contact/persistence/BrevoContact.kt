package net.blueshell.api.platform.integration.contact.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.Identifiable

@Entity
@Table(name = "brevo_contacts")
class BrevoContact(
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "contact_id")
    var contact: Contact,

    @Column(name = "external_id", nullable = false)
    var externalId: Long,
) : Identifiable<Long> {
    @Id
    var contactId: Long = 0

    override val id: Long? get() = contactId
}
