package net.blueshell.blogservice.repository;

import jakarta.validation.constraints.NotNull;
import net.blueshell.blogservice.model.Blog;
import net.blueshell.db.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BlogRepository extends BaseRepository<Blog, UUID> {

    @Query("SELECT n FROM Blog n ORDER BY n.publishedAt DESC")
    @Override
    Page<Blog> findAll(@NotNull Pageable pageable);

    @Query("SELECT n FROM Blog n ORDER BY n.publishedAt DESC")
    @Override
    List<Blog> findAll();
}
