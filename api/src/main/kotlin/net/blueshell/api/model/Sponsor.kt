package net.blueshell.api.model

import jakarta.persistence.*
import net.blueshell.api.base.BaseModel
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import kotlin.properties.Delegates

@Entity
@Table(
    name = "sponsors",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_sponsors_name_deleted_at",
        columnNames = ["name", "deleted_at"]
    ), UniqueConstraint(name = "uk_sponsors_logo_deleted_at", columnNames = ["logo_id", "deleted_at"])],
    indexes = [Index(name = "idx_sponsors_deleted_at", columnList = "deleted_at"), Index(
        name = "idx_sponsors_logo_id",
        columnList = "logo_id"
    )]
)
@SQLDelete(sql = "UPDATE sponsors SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Sponsor : BaseModel() {
    @Column(nullable = false)
    lateinit var name: String

    @Column(nullable = false, length = 4095)
    lateinit var description: String

    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_id", nullable = false, insertable = false, updatable = false)
    lateinit var picture: File

    @Column(name = "logo_id", nullable = false)
    var pictureId: Long = 0
}
