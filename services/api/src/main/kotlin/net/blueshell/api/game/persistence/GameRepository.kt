package net.blueshell.api.game.persistence

import net.blueshell.api.shared.model.SoftDelete
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface GameRepository : JpaRepository<Game, Long> {
    fun findByCode(code: String): Game?

    fun findBySlug(slug: String): Game?

    fun findAllByOrderBySortIndexAsc(): List<Game>

    /** Takes a game off the site, keeping its row. Native: `deleted_at` is not written through JPA. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE game SET ${SoftDelete.STAMP} WHERE id = :id", nativeQuery = true)
    fun remove(
        @Param("id") id: Long,
    ): Int

    /** The removed game holding [code], which the entity's restriction would otherwise hide. */
    @Query("SELECT id FROM game WHERE code = :code AND deleted_at < '${SoftDelete.LIVE}'", nativeQuery = true)
    fun findRemovedIdByCode(
        @Param("code") code: String,
    ): Long?

    /** Brings a removed game back at [slug], as played rather than archived. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE game SET deleted_at = '${SoftDelete.LIVE}', archived = FALSE, slug = :slug WHERE id = :id",
        nativeQuery = true,
    )
    fun restore(
        @Param("id") id: Long,
        @Param("slug") slug: String,
    ): Int
}
