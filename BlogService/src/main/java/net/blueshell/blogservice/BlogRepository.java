package net.blueshell.blogservice;

import jakarta.validation.constraints.NotNull;
import net.blueshell.db.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BlogRepository extends BaseRepository<Blog, UUID> {

    @NotNull
    @Query("SELECT n FROM Blog n ORDER BY n.createdAt DESC")
    @Override
    Page<Blog> findAll(@NotNull Pageable pageable);
}
