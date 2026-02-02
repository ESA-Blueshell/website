package net.blueshell.api.repository

import jakarta.validation.constraints.NotNull
import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.Blog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface BlogRepository : BaseRepository<Blog?> {
    @Query("SELECT n FROM Blog n ORDER BY n.publishedAt DESC")
    override fun findAll(@NotNull pageable: @NotNull Pageable): Page<Blog?>

    @Query("SELECT n FROM Blog n ORDER BY n.publishedAt DESC")
    override fun findAll(): MutableList<Blog?>
}
