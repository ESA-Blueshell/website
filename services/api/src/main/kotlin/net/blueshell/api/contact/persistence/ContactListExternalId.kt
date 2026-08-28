package net.blueshell.api.contact.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.model.AutoIdEntity

@Entity
@Table(
    name = "contact_list_external_ids",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_contact_list_external_ids_list_system",
        columnNames = ["contact_list_id", "system"]
    )]
)
class ContactListExternalId(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_list_id", nullable = false)
    var contactList: ContactList,

    @Enumerated(EnumType.STRING)
    @Column(name = "system", nullable = false)
    val system: ContactSystem,

    @Column(name = "external_id", nullable = false)
    var externalId: Long,
) : AutoIdEntity()
