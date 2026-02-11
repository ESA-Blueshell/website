package net.blueshell.api.domain.telemetry.persistence.repository

import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.repository.BaseRepository
import net.blueshell.api.telemetry.persistence.Telemetry
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface TelemetryRepository : BaseRepository<Telemetry, Long> {
    @Query("SELECT n FROM Telemetry n ORDER BY n.createdAt DESC")
    override fun findAll(@NotNull pageable: @NotNull Pageable): Page<Telemetry>

    @Query("SELECT n FROM Telemetry n ORDER BY n.createdAt DESC")
    override fun findAll(): MutableList<Telemetry>
}
