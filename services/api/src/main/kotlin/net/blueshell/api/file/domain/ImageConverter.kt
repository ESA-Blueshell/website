package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.FileType

/** The port to the imaging service, and what unit tests stub. */
interface ImageConverter {
    /** The caller closes the answer; an undecodable picture is an [InvalidFileException]. */
    fun master(
        source: ScratchFile,
        declaredMediaType: String,
        type: FileType,
    ): Converted

    /** Writes one width at a time, so a mostly stored ladder costs only its missing widths. */
    fun renditionsOf(
        master: ScratchFile,
        mediaType: String,
        type: FileType,
        recorded: ImageDimensions.Size,
        name: String,
    ): Renditions

    class Converted(
        val bytes: ScratchFile,
        val size: ImageDimensions.Size,
    )

    interface Renditions : AutoCloseable {
        val size: ImageDimensions.Size

        /** A [WebpConversionException] where the service refuses the width. */
        fun write(
            size: ImageDimensions.Size,
            output: ScratchFile,
        )
    }
}
