package net.blueshell.api.contribution.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class IngIncassoFileTest {
    private val header = IngHeader("Blueshell E-Sports Vereniging", "NL19INGB0008096462", "NL00ZZZ000000000000", LocalDate.of(2026, 11, 1))

    private fun collection(name: String) =
        IngCollection(
            name,
            "NL91ABNA0417164300",
            "BLUESHELL-1-20250903",
            25.0,
            "Contributie 2026-2027 ESA Blueshell",
            LocalDate.of(2025, 9, 3),
        )

    private fun parts(bytes: ByteArray): Map<String, String> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.associate { it.name to zip.readBytes().decodeToString() }
        }

    @Test
    fun `fills ING's own template, header and rows, and keeps its validations and table`() {
        val file = parts(IngIncassoFile.write(header, listOf(collection("Mila Vries"), collection("Zoe Bakker"))))
        val sheet = file.getValue("xl/worksheets/sheet1.xml")

        assertThat(sheet).contains(
            """<c r="C1" s="31" t="inlineStr"><is><t>Blueshell E-Sports Vereniging</t></is></c>""",
            """<c r="C2" s="31" t="inlineStr"><is><t>NL19INGB0008096462</t></is></c>""",
            """<c r="C3" s="31" t="inlineStr"><is><t>NL00ZZZ000000000000</t></is></c>""",
            """<c r="C4" s="14"><v>46327</v></c>""",
            """<t>Core</t>""",
            """<t>Doorlopend</t>""",
            """<c r="B12" s="28" t="inlineStr"><is><t>Mila Vries</t></is></c>""",
            """<c r="B13" s="29" t="inlineStr"><is><t>Zoe Bakker</t></is></c>""",
            """<c r="E13" s="16"><v>25.0</v></c>""",
            """<c r="G13" s="6"><v>45903</v></c>""",
            "<f>SUM(Table13[Bedrag])</f><v>50.0</v>",
            "<f>COUNT(Table13[Bedrag])</f><v>2</v>",
            """<dataValidations count="16">""",
        )
        assertThat(sheet).contains("""<c r="B14" s="29"/>""")
        assertThat(file.getValue("xl/workbook.xml")).contains("""<calcPr fullCalcOnLoad="1" """)
        assertThat(file).containsKeys("xl/tables/table1.xml", "xl/styles.xml", "xl/worksheets/sheet2.xml")
        // Excel refuses a workbook with one malformed part, so every part has to parse.
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        file.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.values.forEach { part ->
            parser.parse(ByteArrayInputStream(part.encodeToByteArray()))
        }
    }

    @Test
    fun `holds 1 to 1000 collections, and escapes what it writes`() {
        assertThatThrownBy { IngIncassoFile.write(header, emptyList()) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            IngIncassoFile.write(
                header,
                List(1001) { collection("A") },
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        val sheet = parts(IngIncassoFile.write(header, listOf(collection("A & B <C>")))).getValue("xl/worksheets/sheet1.xml")
        assertThat(sheet).contains("<t>A &amp; B &lt;C&gt;</t>")
        assertThat(collection("A").toString()).doesNotContain("0417").contains("4300")
    }
}
