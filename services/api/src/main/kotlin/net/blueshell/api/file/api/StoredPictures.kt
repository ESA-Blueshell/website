package net.blueshell.api.file.api

import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import org.springframework.stereotype.Component

/**
 * The picture a save points at.
 *
 * A picture is uploaded on its own and put on a record when the edit is saved, so a write
 * carries where it is stored rather than its bytes. This turns that back into the file and
 * refuses anything that is not a picture of the kind the field expects. Published here beside
 * the lookup it wraps, since every page that takes an upload resolves one the same way.
 */
@Component
class StoredPictures(
    private val files: FileService,
) {
    fun of(path: String?, kind: FileType): File? {
        val stored = path?.trim()?.ifBlank { null } ?: return null
        return files.findPublicImage(stored, kind) ?: throw PictureNotStored()
    }
}

/**
 * A save named a picture that is not in storage.
 *
 * Thrown rather than answered with nothing, because nothing already means the save named no
 * picture at all and the two are opposites: one is a field left empty, the other is a field
 * pointing at something that was never stored. It carries no facts — the path a caller sent is
 * the caller's own and repeating it back says nothing it does not already know.
 */
class PictureNotStored : RuntimeException(SUMMARY) {
    companion object {
        /** The sentence the answer carries, said once so the advice and the throw agree. */
        const val SUMMARY = "That picture is not in storage."
    }
}
