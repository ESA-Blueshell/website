package net.blueshell.api.file.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository
import java.util.*

@Suppress("FunctionName")
@Repository
interface FileRepository : BaseRepository<File, Long> {
    fun findByName(name: String): Optional<File>

    fun findFirstBy_eventBanners_Id_EventId(eventId: Long): Optional<File>

    fun findByPath(path: String): Optional<File>
}
