package net.blueshell.api.file.api

import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import org.springframework.stereotype.Component

/**
 * The picture a save points at.
 *
 * Pictures are uploaded on their own and put on a record when the edit that chose them is
 * saved, so what a write carries is where a picture is stored rather than its bytes. This
 * turns that back into the file, and refuses anything that is not a picture of the kind the
 * field expects — a banner field takes a banner, and a path that names nothing names nothing.
 *
 * Any page that takes an upload resolves it the same way, so it is published here beside the
 * lookup it wraps rather than kept by whichever module needed it first.
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
