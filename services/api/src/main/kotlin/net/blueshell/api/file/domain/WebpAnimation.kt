package net.blueshell.api.file.domain

/**
 * What a stored WebP says about its own frames, read from `webpmux -info`.
 *
 * The frame table is the only place the per-frame durations are written down. Losing them would
 * make every rendition of a banner run at some invented speed, so they are read back out of the
 * master rather than guessed or stored beside it.
 */
data class WebpAnimation(
    val durationsMillis: List<Int>,
) {
    val frameCount: Int get() = durationsMillis.size

    companion object {
        /**
         * The animation [info] describes, or nothing where it describes a still.
         *
         * `webpmux -info` prints a header, then for an animation a fixed-width table whose
         * columns are numbered from one. A still says `No features present.` and has no table,
         * which is the same answer as a file this cannot parse: a picture with one frame is a
         * still however it is stored.
         */
        fun of(info: String): WebpAnimation? {
            if (!info.lineSequence().any { it.trim().startsWith(FEATURES) && it.contains(ANIMATION) }) return null
            val durations =
                info
                    .lineSequence()
                    .mapNotNull(::durationOf)
                    .toList()
            return durations.takeIf { it.size > 1 }?.let(::WebpAnimation)
        }

        /** The duration on one row of the frame table, or nothing where the line is not one. */
        private fun durationOf(line: String): Int? {
            val match = FRAME_ROW.find(line.trim()) ?: return null
            return match.groupValues[1].toIntOrNull()
        }

        private const val FEATURES = "Features present"
        private const val ANIMATION = "animation"

        /**
         * A row of the frame table: an index, then width, height, alpha, both offsets and the
         * duration. Anchored on the leading index and counted out by column, because the header
         * names them and the rows do not.
         */
        private val FRAME_ROW = Regex("""^\d+:\s+\d+\s+\d+\s+\S+\s+\d+\s+\d+\s+(\d+)\b""")
    }
}
