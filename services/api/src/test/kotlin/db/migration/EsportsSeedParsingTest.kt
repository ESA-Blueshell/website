package db.migration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EsportsSeedParsingTest {

    @Test
    fun `reads a row against the header rather than by position`() {
        val rows = R__Esports_seed.parse("name,start_date,end_date\nAutumn 2020,2020-09-01,2021-01-31\n")

        assertThat(rows).singleElement().isEqualTo(
            mapOf("name" to "Autumn 2020", "start_date" to "2020-09-01", "end_date" to "2021-01-31"),
        )
    }

    @Test
    fun `a quoted field keeps the comma inside it`() {
        // A team is free to have a comma in its name; it is still one field.
        val rows = R__Esports_seed.parse("game,name\nVALORANT,\"BS Ohm, Sweet Ohm\"\n")

        assertThat(rows.single()["name"]).isEqualTo("BS Ohm, Sweet Ohm")
    }

    @Test
    fun `a doubled quote inside a quoted field is one quote`() {
        val rows = R__Esports_seed.parse("handle\n\"the \"\"wall\"\"\"\n")

        assertThat(rows.single()["handle"]).isEqualTo("the \"wall\"")
    }

    @Test
    fun `an empty field is empty rather than absent`() {
        val rows = R__Esports_seed.parse("game,name,image\nSMASH,BS Smashers,\n")

        assertThat(rows.single()).containsEntry("image", "")
    }

    @Test
    fun `a file with only a header holds no records`() {
        assertThat(R__Esports_seed.parse("name,start_date,end_date\n")).isEmpty()
    }

    @Test
    fun `a blank line is not a record`() {
        val rows = R__Esports_seed.parse("name\nAutumn 2020\n\n")

        assertThat(rows).hasSize(1)
    }

    @Test
    fun `a row that does not fit the header is refused rather than silently shifted`() {
        // A missing comma would otherwise put a season's end date into its start.
        assertThatThrownBy { R__Esports_seed.parse("name,start_date,end_date\nAutumn,2020-09-01\n") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("header has 3")
    }
}
