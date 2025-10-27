package net.blueshell.api.repository;

import jakarta.validation.constraints.NotNull;
import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.Blog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlogRepository extends BaseRepository<Blog> {

    @org.jetbrains.annotations.NotNull
    @Query("SELECT n FROM Blog n ORDER BY n.publishedAt DESC")
    @Override
    Page<Blog> findAll(@org.jetbrains.annotations.NotNull @NotNull Pageable pageable);

    @org.jetbrains.annotations.NotNull
    @Query("SELECT n FROM Blog n ORDER BY n.publishedAt DESC")
    @Override
    List<Blog> findAll();
}
