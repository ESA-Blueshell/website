package net.blueshell.api.esports.domain

import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import org.springframework.stereotype.Component

/**
 * The picture a save points at.
 *
 * Pictures are uploaded on their own and put on a record when the dialog that chose them is
 * saved, so what a write carries is where a picture is stored rather than its bytes. This
 * turns that back into the file, and refuses anything that is not a picture of the kind the
 * field expects — a banner field takes a banner, and a path that names nothing names nothing.
 */
@Component
class EsportsPictures(
    private val files: FileService,
) {
    fun of(path: String?, kind: FileType): File? {
        val stored = path?.trim()?.ifBlank { null } ?: return null
        return files.findPublicImage(stored, kind) ?: throw PictureNotStoredException()
    }
}

