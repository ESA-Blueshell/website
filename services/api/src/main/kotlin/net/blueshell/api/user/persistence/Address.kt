package net.blueshell.api.user.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "addresses",
    indexes = [
        Index(name = "idx_addresses_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_addresses_city", columnList = "city"),
        Index(name = "idx_addresses_zip_code", columnList = "zip_code")
    ]
)
@SQLDelete(sql = "UPDATE addresses SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Address(
    @OneToOne(mappedBy = "address")
    val user: User,

    @Column
    var country: String? = null,

    @Column
    var city: String? = null,

    @Column
    var street: String? = null,

    @Column
    var houseNumber: String? = null,

    @Column
    var zipCode: String? = null,
) : AuditedAutoIdEntity()
