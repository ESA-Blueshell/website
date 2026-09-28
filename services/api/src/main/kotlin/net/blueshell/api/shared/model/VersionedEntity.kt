package net.blueshell.api.shared.model

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import org.hibernate.annotations.ColumnDefault
import org.springframework.dao.OptimisticLockingFailureException

@MappedSuperclass
abstract class VersionedEntity {
    @Version
    @Column(name = "version", nullable = false)
    @ColumnDefault("0")
    var version: Long = 0L

    /**
     * Refuses a write made against [seen] once somebody else has saved over it. Hibernate checks
     * the version it loaded, so copying a request's version onto a loaded entity checks nothing.
     */
    fun requireVersion(seen: Long) {
        if (seen != version) {
            throw OptimisticLockingFailureException("${this::class.simpleName} is at version $version, not $seen")
        }
    }
}
