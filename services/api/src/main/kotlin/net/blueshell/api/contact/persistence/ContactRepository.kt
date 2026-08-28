package net.blueshell.api.contact.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ContactRepository : BaseRepository<Contact, Long> {
    fun findByUserId(userId: Long): Contact?

    /** Bypasses @SQLRestriction to include soft-deleted contacts. Native SQL required to skip the filter. */
    @Query(value = "SELECT * FROM contacts WHERE user_id = :userId LIMIT 1", nativeQuery = true)
    fun findByUserIdIncludingDeleted(@Param("userId") userId: Long): Contact?

    /**
     * Soft-deletes a Contact by ID using native SQL, bypassing Hibernate's cascade machinery.
     * This preserves ContactExternalId records so per-integration sync jobs can still read them.
     */
    @Modifying
    @Query(
        value = "UPDATE contacts SET deleted_at = NOW(), version = version + 1 WHERE id = :id",
        nativeQuery = true
    )
    fun softDeleteById(@Param("id") id: Long)
}
