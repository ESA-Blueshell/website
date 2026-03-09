package net.blueshell.api.platform.integration.contact.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.model.AutoIdEntity

@Entity
@Table(
    name = "contact_external_ids",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_contact_external_ids_contact_system",
        columnNames = ["contact_id", "system"]
    )]
)
class ContactExternalId(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    var contact: Contact,

    @Enumerated(EnumType.STRING)
    @Column(name = "system", nullable = false)
    val system: ContactSystem,

    @Column(name = "external_id", nullable = false)
    var externalId: Long,
) : AutoIdEntity()
