package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.FileType

/**
 * Turns pictures into WebP: an upload into its master at the kind's ceiling, and a master into
 * the renditions it is served at. The one door to the converters, so what decodes uploaded bytes
 * can move without its callers changing.
 */
interface ImageConverter {
    /**
     * [source] as a WebP master within [type]'s ceiling, keeping its frames where it has more than
     * one. [declaredMediaType] is the uploader's claim and says which decoder to try first.
     *
     * The caller closes the answer. A picture that cannot be decoded is an [InvalidFileException].
     */
    fun master(
        source: ScratchFile,
        declaredMediaType: String,
        type: FileType,
    ): Converted

    /**
     * The renditions of a stored [master], written one width at a time as they are asked for, so a
     * ladder that is mostly stored already costs one decode and not one per width.
     *
     * [recorded] is the size on the master's record; [name] is what a log line calls the picture.
     */
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
        /** What the widths are measured against: an animation's canvas, or the recorded size of a still. */
        val size: ImageDimensions.Size

        /** One width, written into [output]. A [WebpConversionException] where the converter refuses it. */
        fun write(
            size: ImageDimensions.Size,
            output: ScratchFile,
        )
    }
}
