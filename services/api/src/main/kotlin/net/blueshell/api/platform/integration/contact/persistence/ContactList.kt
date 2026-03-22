package net.blueshell.api.platform.integration.contact.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "contact_lists",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_contact_lists_name_deleted_at",
        columnNames = ["name", "deleted_at"]
    )],
    indexes = [
        Index(name = "idx_contact_lists_deleted_at", columnList = "deleted_at"),
    ]
)
@SQLDelete(sql = "UPDATE contact_lists SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class ContactList(
    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "folder_name")
    var folderName: String? = null,
) : AuditedAutoIdEntity() {

    @OneToMany(mappedBy = "contactList", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _externalIds: MutableList<ContactListExternalId> = mutableListOf()

    fun externalListId(system: ContactSystem): Long? =
        _externalIds.find { it.system == system }?.externalId

    fun setExternalListId(system: ContactSystem, id: Long) {
        val existing = _externalIds.find { it.system == system }
        if (existing != null) existing.externalId = id
        else _externalIds.add(ContactListExternalId(contactList = this, system = system, externalId = id))
    }
}
