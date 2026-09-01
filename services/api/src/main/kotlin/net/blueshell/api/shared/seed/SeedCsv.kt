package net.blueshell.api.shared.seed

/**
 * The comma-separated seed files under one classpath directory.
 *
 * More than one thing reads a seed directory and they must agree about what a row says. A
 * repeatable migration puts the rows into the database before the application is up, and a
 * start-up step puts the art on them once it is, because storing a picture needs the storage
 * volume and the converter that a migration runner has neither of. ONE reader across the whole
 * application rather than one each, so a quoted team name cannot parse two ways depending on
 * which seed is looking at it.
 *
 * Quoting follows the usual convention, and only where a field needs it: a team called
 * "BS Ohm, Sweet Ohm" is one field, not two, and a doubled quote inside a quoted field is one
 * quote.
 *
 * The directory is bound once, here, rather than named at every call: each seed has its own
 * — the esports history and the association's boards are separate sets of files — and a reader
 * that carried it as an argument would repeat it at every read and let two of them disagree.
 */
class SeedCsv(
    /** Where the files live, as a classpath directory. */
    val directory: String,
) {

    /** One seed file's contents, by name. Absence is a packaging fault rather than a state. */
    fun read(name: String): String =
        javaClass.classLoader.getResourceAsStream("$directory/$name")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Seed file $directory/$name is missing")

    /** The rows of one seed file, by name. */
    fun rows(name: String): List<Map<String, String>> = parse(read(name))

    companion object {

        /**
         * The rows of [content], each read against the header rather than by position.
         *
         * A row whose field count does not match the header is refused rather than shifted: a
         * missing comma would otherwise put a season's end date into its start, and land as data.
         */
        fun parse(content: String): List<Map<String, String>> {
            val rows = splitRows(content).filter { row -> row.any { it.isNotBlank() } }
            if (rows.isEmpty()) return emptyList()
            val header = rows.first()
            return rows.drop(1).map { cells ->
                require(cells.size == header.size) {
                    "Row has ${cells.size} fields, header has ${header.size}: $cells"
                }
                header.zip(cells).toMap()
            }
        }

        private fun splitRows(content: String): List<List<String>> {
            val rows = mutableListOf<List<String>>()
            var cells = mutableListOf<String>()
            val cell = StringBuilder()
            var quoted = false
            var index = 0
            while (index < content.length) {
                val char = content[index]
                when {
                    quoted && char == '"' && content.getOrNull(index + 1) == '"' -> { cell.append('"'); index += 1 }
                    char == '"' -> quoted = !quoted
                    !quoted && char == ',' -> { cells.add(cell.toString()); cell.clear() }
                    !quoted && (char == '\n' || char == '\r') -> {
                        if (char == '\r' && content.getOrNull(index + 1) == '\n') index += 1
                        cells.add(cell.toString()); cell.clear()
                        rows.add(cells); cells = mutableListOf()
                    }
                    else -> cell.append(char)
                }
                index += 1
            }
            if (cell.isNotEmpty() || cells.isNotEmpty()) { cells.add(cell.toString()); rows.add(cells) }
            return rows
        }
    }
}
