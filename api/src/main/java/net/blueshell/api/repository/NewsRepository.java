package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.News;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsRepository extends BaseRepository<News, Long> {

    @NotNull
    @Query("SELECT n FROM News n ORDER BY n.postedAt DESC")
    @Override
    Page<News> findAll(@NotNull Pageable pageable);
}
