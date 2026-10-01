package net.blueshell.api.event.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface EventRepository : BaseRepository<Event, Long> {
    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(
        spec: Specification<Event>,
        pageable: Pageable,
    ): Page<Event>

    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(pageable: Pageable): Page<Event>

    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(): MutableList<Event>

    @Query(value = "SELECT * FROM events WHERE id = :id", nativeQuery = true)
    fun findByIdIncludingDeleted(id: Long): Event?

    @Query(
        "select e.id from Event e where (e.approved = true or e.awaitingReapproval = true) and e.endTime >= :from",
    )
    fun findKeptIdsEndingFrom(
        @Param("from") from: Instant,
    ): List<Long>

    /** Gives every approved event not over yet that has no announce at the one passed; answers how many. */
    @Modifying
    @Query("update Event e set e.announceAt = :at where e.approved = true and e.announceAt is null and e.endTime > :now")
    fun announceUnannouncedAt(
        @Param("at") at: Instant,
        @Param("now") now: Instant,
    ): Int

    @Query("select e.id from Event e where e.endTime >= :from")
    fun findIdsEndingFrom(
        @Param("from") from: Instant,
    ): List<Long>

    fun existsByTitle(title: String): Boolean

    @Query("select count(e) from Event e join e.gameCodes code where code = :code")
    fun countNamingGame(
        @Param("code") code: String,
    ): Long
}
