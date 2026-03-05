package net.blueshell.api.platform.integration.contact.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "contacts",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_contacts_user_id_deleted_at",
        columnNames = ["user_id", "deleted_at"]
    )],
    indexes = [
        Index(name = "idx_contacts_user_id", columnList = "user_id"),
        Index(name = "idx_contacts_deleted_at", columnList = "deleted_at"),
    ]
)
@SQLDelete(sql = "UPDATE contacts SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Contact(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "synced_email", nullable = false)
    var syncedEmail: String = "",

    @Column(name = "synced_first_name", nullable = false)
    var syncedFirstName: String = "",

    @Column(name = "synced_last_name", nullable = false)
    var syncedLastName: String = "",

    @Column(name = "synced_phone_number")
    var syncedPhoneNumber: String? = null,

    @Column(name = "synced_newsletter", nullable = false)
    var syncedNewsletter: Boolean = false,

    @Column(name = "synced_is_member", nullable = false)
    var syncedIsMember: Boolean = false,
) : AuditedAutoIdEntity() {

    @OneToOne(mappedBy = "contact", cascade = [CascadeType.ALL], orphanRemoval = true)
    var listmonkContact: ListmonkContact? = null

    @OneToOne(mappedBy = "contact", cascade = [CascadeType.ALL], orphanRemoval = true)
    var brevoContact: BrevoContact? = null
}
