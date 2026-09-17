package net.blueshell.api.user.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
@Suppress("FunctionName")
interface RoleChangeRepository : BaseRepository<RoleChange, Long> {
    /** One person's history, newest first, with both people fetched so a listing stays one query. */
    @Query(
        """
        SELECT c FROM RoleChange c
        JOIN FETCH c.actor
        JOIN FETCH c.subject
        WHERE c.subject.id = :subjectId
        ORDER BY c.changedAt DESC, c.id DESC
        """,
    )
    fun findBySubjectNewestFirst(@Param("subjectId") subjectId: Long): List<RoleChange>

    /**
     * How many people could still sign in and reach everything. The service account is left out
     * because nobody signs in as it, and a soft-deleted account because it is not a way back in.
     *
     * Native, because `@SQLRestriction` on [User] hides the soft-deleted rows this has to weigh
     * and `authorities` is an element collection rather than an entity.
     *
     * `FOR UPDATE` locks the administrators' rows for the rest of the caller's transaction, so two
     * admins stepping down at the same moment are serialised: the second reads the tally the first
     * left behind and is refused, rather than both reading two and both committing.
     */
    @Query(
        value = """
        SELECT COUNT(DISTINCT a.user_id)
        FROM authorities a
                 JOIN users u ON u.id = a.user_id
        WHERE a.authority = 'ADMIN'
          AND u.deleted_at = '9999-12-31 23:59:59'
          AND NOT EXISTS (SELECT 1 FROM authorities s WHERE s.user_id = u.id AND s.authority = 'SYSTEM')
        FOR UPDATE
        """,
        nativeQuery = true,
    )
    fun countAdministratorsForUpdate(): Long
}
