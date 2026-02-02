package net.blueshell.api.model.event

import jakarta.persistence.*
import lombok.Data
import lombok.EqualsAndHashCode
import lombok.NoArgsConstructor
import lombok.ToString
import net.blueshell.api.base.BaseModel
import net.blueshell.api.base.JpaListener
import net.blueshell.api.model.File
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "event_banners",
    uniqueConstraints = [UniqueConstraint(name = "uk_event_file", columnNames = ["event_id", "file_id", "deleted_at"])],
    indexes = [Index(
        name = "idx_event_banners_deleted_at",
        columnList = "deleted_at"
    ), Index(name = "idx_event_banners_event", columnList = "event_id"), Index(
        name = "idx_event_banners_file",
        columnList = "file_id"
    )]
)
@SQLDelete(sql = "UPDATE event_banners SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
class EventBanner : BaseModel() {
    @Column(name = "event_id", nullable = false, insertable = false, updatable = false)
    @ToString.Include
    private var eventId: Long? = null

    @Column(name = "file_id", nullable = false, insertable = false, updatable = false)
    @ToString.Include
    private var fileId: Long? = null

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private val event: Event? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false, foreignKey = ForeignKey(name = "fk_event_banners_file"))
    private val file: File? = null
}
