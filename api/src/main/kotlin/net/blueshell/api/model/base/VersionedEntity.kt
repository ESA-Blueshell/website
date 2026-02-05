package net.blueshell.api.model.base

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import org.hibernate.annotations.ColumnDefault

@MappedSuperclass
abstract class VersionedEntity {
    @Version
    @Column(name = "version", nullable = false)
    @ColumnDefault("0")
    var version: Long = 0L
}
