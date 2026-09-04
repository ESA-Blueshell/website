package net.blueshell.api.shared.seed

/**
 * The comma-separated seed files under one classpath directory.
 *
 * The migration and the start-up art step both read these files and must agree about what a row
 * says, so there is one reader rather than one each: a team called "BS Ohm, Sweet Ohm" cannot
 * parse two ways depending on who is looking. Quoting follows the usual convention, and a
 * doubled quote inside a quoted field is one quote. The directory is bound once here rather
 * than passed per call, so two readers of one seed cannot disagree about which files they mean.
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
