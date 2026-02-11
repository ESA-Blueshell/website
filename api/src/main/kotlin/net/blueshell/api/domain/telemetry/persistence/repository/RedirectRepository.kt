package net.blueshell.api.domain.telemetry.persistence.repository

import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.repository.BaseRepository
import net.blueshell.api.telemetry.persistence.Redirect
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface RedirectRepository : BaseRepository<Redirect, Long> {
    @Query("SELECT n FROM Redirect n ORDER BY n.createdAt DESC")
    override fun findAll(@NotNull pageable: @NotNull Pageable): Page<Redirect>

    @Query("SELECT n FROM Redirect n ORDER BY n.createdAt DESC")
    override fun findAll(): MutableList<Redirect>

    @Query("SELECT e FROM Redirect e WHERE e.createdAt >= :from AND e.createdAt <= :to ORDER BY e.createdAt DESC")
    fun findCreatedAtBetween(from: OffsetDateTime, to: OffsetDateTime): MutableList<Redirect>
}
