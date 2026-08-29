package net.blueshell.api.file.persistence

import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    /**
     * The pictures of these kinds that somebody uploaded, rather than the copies derived from
     * them.
     *
     * Asked for by the startup repair, which offers every one of them its widths again. A
     * picture whose widths are all present is left alone, so the second start does nothing but
     * look.
     */
    @Query("SELECT f FROM File f WHERE f.renditionWidth IS NULL AND f.type IN :types")
    fun findSourcesOfTypes(@Param("types") types: Collection<FileType>): List<File>
}
