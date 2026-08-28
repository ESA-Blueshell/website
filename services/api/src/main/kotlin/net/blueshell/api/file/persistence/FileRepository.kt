package net.blueshell.api.file.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface FileRepository : BaseRepository<File, Long> {
    fun findByName(name: String): Optional<File>

    fun findByPath(path: String): Optional<File>
}