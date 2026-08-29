package net.blueshell.api.file.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface FileRepository : BaseRepository<File, Long> {
    fun findByName(name: String): Optional<File>

    fun findByPath(path: String): Optional<File>

    /**
     * Pictures stored before their size was recorded.
     *
     * Narrowed to image media types so a document is not opened looking for a width it was
     * never going to have. This is the same question `ImageDimensions.mayHaveSize` asks in
     * Kotlin, written twice because SQL cannot call it; change one, change the other.
     *
     * A picture measured once and found unreadable is returned again on the next start, which
     * costs one header read of a file already on the disk.
     */
    @Query("SELECT f FROM File f WHERE f.width IS NULL AND f.mediaType LIKE 'image/%'")
    fun findImagesMissingDimensions(): List<File>
}