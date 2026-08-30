package net.blueshell.api.esports.domain

/**
 * Reads the comma-separated files under `db/seed/esports`.
 *
 * Two things read those files and they must agree about what a row says. The migration puts
 * the games, seasons, teams and rosters into the database before the application is up;
 * [ShippedArt] puts the art on them once it is, because storing a picture needs the storage
 * volume and the converter that a migration runner has neither of. One reader rather than one
 * each, so a quoted team name cannot parse two ways depending on which of them is looking.
 *
 * Quoting follows the usual convention, and only where a field needs it: a team called
 * "BS Ohm, Sweet Ohm" is one field, not two, and a doubled quote inside a quoted field is one
 * quote.
 */
object SeedCsv {

    /** Where the files live, as a classpath directory. */
    const val DIRECTORY = "db/seed/esports"

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
            require(cells.size == header.size) { "Row has ${cells.size} fields, header has ${header.size}: $cells" }
            header.zip(cells).toMap()
        }
    }

    /** One seed file's contents, by name. Absence is a packaging fault rather than a state. */
    fun read(name: String): String =
        SeedCsv::class.java.classLoader.getResourceAsStream("$DIRECTORY/$name")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Seed file $DIRECTORY/$name is missing")

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
