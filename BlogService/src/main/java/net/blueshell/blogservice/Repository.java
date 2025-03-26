package net.blueshell.blogservice;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.News;
import net.blueshell.db.BaseRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

@org.springframework.stereotype.Repository
public interface Repository extends BaseRepository<News, Long>, net.blueshell.db.BaseRepository<Blog, Long> {

    @NotNull
    @Query("SELECT n FROM News n ORDER BY n.postedAt DESC")
    @Override
    Page<News> findAll(@NotNull Pageable pageable);
}
