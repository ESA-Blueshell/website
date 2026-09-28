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
     * Refuses a write made against [seen] once somebody else has saved over it, called right after
     * loading and before any other refusal. An entity loaded inside a transaction stays managed,
     * and Hibernate then checks the version it loaded, not one copied onto it afterwards.
     */
    fun requireVersion(seen: Long) {
        if (seen != version) {
            throw OptimisticLockingFailureException("${this::class.simpleName} is at version $version, not $seen")
        }
    }
}
