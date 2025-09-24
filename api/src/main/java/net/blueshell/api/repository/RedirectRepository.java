package net.blueshell.api.repository;

import jakarta.validation.constraints.NotNull;
import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.Redirect;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RedirectRepository extends BaseRepository<Redirect> {

    @org.jetbrains.annotations.NotNull
    @Query("SELECT n FROM Redirect n ORDER BY n.createdAt DESC")
    @Override
    Page<Redirect> findAll(@org.jetbrains.annotations.NotNull @NotNull Pageable pageable);

    @org.jetbrains.annotations.NotNull
    @Query("SELECT n FROM Redirect n ORDER BY n.createdAt DESC")
    @Override
    List<Redirect> findAll();

    @Query("SELECT e FROM Redirect e WHERE e.createdAt >= :from AND e.createdAt <= :to ORDER BY e.createdAt DESC")
    List<Redirect> findCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);
}
