package net.blueshell.api.contribution.domain

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The association's side of ING's batch header. */
data class IngHeader(
    val accountHolder: String,
    val iban: String,
    val incassantId: String,
    val collectionDate: LocalDate,
)

/** One row of ING's batch, already in ING's characters. */
data class IngCollection(
    val name: String,
    val iban: String,
    val mandateReference: String,
    val amount: Double,
    val description: String,
    val mandateSignedOn: LocalDate,
) {
    // Nothing of the account reaches a log line.
    override fun toString(): String = "IngCollection($mandateReference, ****${iban.takeLast(SHOWN)})"

    private companion object {
        const val SHOWN = 4
    }
}

/**
 * ING's own incasso batch template, filled in. Cells are written into the template's sheet as it
 * stands rather than rebuilding the workbook, so ING's validations, protection and table survive
 * and the file passes them in Excel. The result is only ever held in memory.
 */
object IngIncassoFile {
    /** Rows 12 to 1011 of the template's table, so a run with more is split over several files. */
    const val MAX_COLLECTIONS = 1000

    private const val TEMPLATE = "/incasso/incassobatch-template-v1.0.xlsx"
    private const val SHEET = "xl/worksheets/sheet1.xml"
    private const val WORKBOOK = "xl/workbook.xml"
    private const val FIRST_ROW = 12
    private val EXCEL_EPOCH = LocalDate.of(1899, 12, 30)
    private val EMPTY_CELL = Regex("""<c r="([A-Z]+\d+)"( s="\d+")?/>""")

    fun write(
        header: IngHeader,
        collections: List<IngCollection>,
    ): ByteArray {
        require(collections.size in 1..MAX_COLLECTIONS) { "A file holds 1 to $MAX_COLLECTIONS collections" }
        val cells =
            buildMap {
                put("C1", text(header.accountHolder))
                put("C2", text(header.iban))
                put("C3", text(header.incassantId))
                put("C4", day(header.collectionDate))
                put("C5", text("Core"))
                put("C6", text("Doorlopend"))
                collections.forEachIndexed { index, one ->
                    val row = FIRST_ROW + index
                    put("B$row", text(one.name))
                    put("C$row", text(one.iban))
                    put("D$row", text(one.mandateReference))
                    put("E$row", number(one.amount))
                    put("F$row", text(one.description))
                    put("G$row", day(one.mandateSignedOn))
                }
            }
        val template = requireNotNull(javaClass.getResourceAsStream(TEMPLATE)) { "The ING template is missing" }
        val out = ByteArrayOutputStream()
        ZipInputStream(template).use { input ->
            ZipOutputStream(out).use { zip ->
                generateSequence { input.nextEntry }.forEach { entry ->
                    val bytes = input.readBytes()
                    zip.putNextEntry(ZipEntry(entry.name))
                    zip.write(
                        when (entry.name) {
                            SHEET -> filled(bytes.decodeToString(), cells, collections).encodeToByteArray()
                            // The batch total and count are formulas; Excel works them out again on opening.
                            WORKBOOK -> bytes.decodeToString().replace("<calcPr ", "<calcPr fullCalcOnLoad=\"1\" ").encodeToByteArray()
                            else -> bytes
                        },
                    )
                    zip.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

    private fun filled(
        sheet: String,
        cells: Map<String, String>,
        collections: List<IngCollection>,
    ): String {
        val written =
            EMPTY_CELL.replace(sheet) { match ->
                val ref = match.groupValues[1]
                cells[ref]?.let { "<c r=\"$ref\"${match.groupValues[2]}$it</c>" } ?: match.value
            }
        return written
            .replace("<f>SUM(Table13[Bedrag])</f><v>0</v>", "<f>SUM(Table13[Bedrag])</f><v>${collections.sumOf { it.amount }}</v>")
            .replace("<f>COUNT(Table13[Bedrag])</f><v>0</v>", "<f>COUNT(Table13[Bedrag])</f><v>${collections.size}</v>")
    }

    private fun text(value: String) = """ t="inlineStr"><is><t>${escaped(value)}</t></is>"""

    private fun number(value: Double) = "><v>$value</v>"

    private fun day(value: LocalDate) = "><v>${ChronoUnit.DAYS.between(EXCEL_EPOCH, value)}</v>"

    private fun escaped(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
