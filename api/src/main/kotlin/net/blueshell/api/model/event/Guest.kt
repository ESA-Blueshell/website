package net.blueshell.api.model.event

import jakarta.persistence.*
import lombok.Data
import lombok.EqualsAndHashCode
import lombok.NoArgsConstructor
import lombok.ToString
import net.blueshell.api.base.BaseModel
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "guests",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_guests_access_token_deleted_at",
        columnNames = ["access_token", "deleted_at"]
    )],
    indexes = [Index(name = "idx_guests_deleted_at", columnList = "deleted_at"), Index(
        name = "idx_guests_name",
        columnList = "name"
    ), Index(name = "idx_guests_discord", columnList = "discord"), Index(
        name = "idx_guests_created_at",
        columnList = "created_at"
    )]
)
@SQLDelete(sql = "UPDATE guests SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
class Guest : BaseModel() {
    @Column(nullable = false)
    @ToString.Include
    private var name: String? = null

    @Column(nullable = false)
    @ToString.Include
    private var discord: String? = null

    @Column(nullable = false)
    @ToString.Include
    private var email: String? = null

    @Column
    @ToString.Include
    private var phoneNumber: String? = null

    @Column(name = "access_token", nullable = false)
    private var accessToken: String? = null
}
