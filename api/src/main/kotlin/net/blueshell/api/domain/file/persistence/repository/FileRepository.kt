package net.blueshell.api.domain.file.persistence.repository

import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Suppress("FunctionName")
@Repository
interface FileRepository : BaseRepository<File, Long> {
    fun findByName(name: String): Optional<File>

    fun findFirstByEventBannersIdEventId(eventId: Long): Optional<File>

    fun findByPath(path: String): Optional<File>
}