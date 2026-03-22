package net.blueshell.api.platform.integration.contact.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "contact_list_memberships",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_contact_list_memberships",
        columnNames = ["contact_id", "contact_list_id", "deleted_at"]
    )],
    indexes = [
        Index(name = "idx_contact_list_memberships_contact", columnList = "contact_id"),
        Index(name = "idx_contact_list_memberships_list", columnList = "contact_list_id"),
        Index(name = "idx_contact_list_memberships_deleted_at", columnList = "deleted_at"),
    ]
)
@SQLDelete(sql = "UPDATE contact_list_memberships SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class ContactListMembership(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    val contact: Contact,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_list_id", nullable = false)
    val contactList: ContactList,
) : AuditedAutoIdEntity()
