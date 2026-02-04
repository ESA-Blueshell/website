package net.blueshell.api.repository

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.File
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface FileRepository : BaseRepository<File> {
    fun findByName(name: String): Optional<File>

    fun findBy_eventBanners_Id(bannerId: Long): Optional<File>

    fun findByPath(path: String): Optional<File>
}
