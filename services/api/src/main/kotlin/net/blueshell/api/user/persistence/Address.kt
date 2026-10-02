package net.blueshell.api.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.SoftDelete
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "addresses",
    indexes = [
        Index(name = "idx_addresses_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_addresses_city", columnList = "city"),
        Index(name = "idx_addresses_zip_code", columnList = "zip_code"),
    ],
)
@SQLDelete(sql = "UPDATE addresses SET ${SoftDelete.STAMP}, version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction(SoftDelete.ACTIVE)
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
    /**
     * The address sealed through Vault Transit as one value, bound to its member; see `SealedAddresses`.
     * The plaintext columns above are empty once it is set, and go in a later release.
     */
    @Column(name = "sealed_address", columnDefinition = "TEXT")
    var sealed: String? = null,
) : AuditedAutoIdEntity()
