package net.blueshell.api.repository;

import jakarta.validation.constraints.NotNull;
import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.Telemetry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TelemetryRepository extends BaseRepository<Telemetry, UUID> {

    @org.jetbrains.annotations.NotNull
    @Query("SELECT n FROM Telemetry n ORDER BY n.createdAt DESC")
    @Override
    Page<Telemetry> findAll(@org.jetbrains.annotations.NotNull @NotNull Pageable pageable);

    @org.jetbrains.annotations.NotNull
    @Query("SELECT n FROM Telemetry n ORDER BY n.createdAt DESC")
    @Override
    List<Telemetry> findAll();
}
