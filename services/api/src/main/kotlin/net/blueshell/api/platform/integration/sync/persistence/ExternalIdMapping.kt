package net.blueshell.api.platform.integration.sync.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/** Maps a domain aggregate (User, Event, ContactList, …) to its id in one external system. */
@Entity
@Table(
    name = "external_id_mapping",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_external_id_mapping",
        columnNames = ["aggregate_type", "aggregate_id", "system"]
    )]
)
class ExternalIdMapping(
    @Column(name = "aggregate_type", nullable = false, length = 64)
    val aggregateType: String,

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: Long,

    @Column(name = "system", nullable = false, length = 64)
    val system: String,

    @Column(name = "external_id", length = 255)
    var externalId: String? = null,

    @Column(name = "synced_version")
    var syncedVersion: Long? = null,
) : AutoIdEntity() {

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    var createdAt: Instant = Instant.EPOCH

    @Column(name = "updated_at", nullable = false, insertable = false)
    var updatedAt: Instant = Instant.EPOCH
}
