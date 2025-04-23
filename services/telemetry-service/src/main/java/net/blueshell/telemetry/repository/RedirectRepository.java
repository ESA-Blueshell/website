package net.blueshell.telemetry.repository;

import jakarta.validation.constraints.NotNull;
import net.blueshell.db.BaseRepository;
import net.blueshell.telemetry.model.Redirect;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RedirectRepository extends BaseRepository<Redirect, UUID> {

    @Query("SELECT n FROM Redirect n ORDER BY n.createdAt DESC")
    @Override
    Page<Redirect> findAll(@NotNull Pageable pageable);

    @Query("SELECT n FROM Redirect n ORDER BY n.createdAt DESC")
    @Override
    List<Redirect> findAll();

    @Query("SELECT e FROM Redirect e WHERE e.createdAt >= :from AND e.createdAt <= :to ORDER BY e.createdAt DESC")
    List<Redirect>  findCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);
}
