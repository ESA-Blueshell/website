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
     * The same question `ImageDimensions.mayHaveSize` asks, written twice because SQL cannot
     * call it: change one, change the other. A picture found unreadable comes back on the next
     * start, at the cost of one header read.
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
