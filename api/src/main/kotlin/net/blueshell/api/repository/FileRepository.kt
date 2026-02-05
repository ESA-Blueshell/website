package net.blueshell.api.repository

import net.blueshell.api.repository.base.BaseRepository
import net.blueshell.api.model.File
import org.springframework.stereotype.Repository
import java.util.*

@Suppress("FunctionName")
@Repository
interface FileRepository : BaseRepository<File, Long> {
    fun findByName(name: String): Optional<File>

    fun findFirstBy_eventBanners_Id_EventId(eventId: Long): Optional<File>

    fun findByPath(path: String): Optional<File>
}
