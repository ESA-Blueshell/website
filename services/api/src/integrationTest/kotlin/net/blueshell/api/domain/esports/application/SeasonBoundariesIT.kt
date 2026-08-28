package net.blueshell.api.domain.esports.application

import net.blueshell.api.domain.esports.application.exception.SeasonDatesReversedException
import net.blueshell.api.domain.esports.application.exception.SeasonOverlapException
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

/**
 * A roster belongs to the season it played in, so two seasons running at once make "which
 * one" unanswerable. Nothing stopped them being written down until now.
 */
@SpringBootTest
class SeasonBoundariesIT : UserTestSupport() {
    @Autowired private lateinit var seasons: SeasonService

    private fun autumn(year: Int) = seasons.create(
        "Autumn $year ${System.nanoTime()}",
        LocalDate.of(year, 9, 1),
        LocalDate.of(year + 1, 1, 31),
    )

    @Test
    fun `a season that covers ground another already covers is refused, and says which`() {
        val existing = autumn(2040)

        assertThatThrownBy {
            seasons.create("Clashing", LocalDate.of(2040, 11, 1), LocalDate.of(2041, 3, 31))
        }
            .isInstanceOf(SeasonOverlapException::class.java)
            // Named, so the objection can be read on the form that caused it.
            .hasMessageContaining(existing.name)
    }

    @Test
    fun `a season that meets another without covering it is allowed`() {
        autumn(2041)

        val spring = seasons.create("Spring 2041/42", LocalDate.of(2042, 2, 1), LocalDate.of(2042, 8, 31))

        assertThat(spring.id).isNotNull()
    }

    @Test
    fun `a season may cover the ground it already covers`() {
        val existing = autumn(2042)

        // Renaming without moving must not be read as clashing with itself.
        val renamed = seasons.update(existing.id!!, "Autumn, renamed", existing.startDate, existing.endDate)

        assertThat(renamed.name).isEqualTo("Autumn, renamed")
    }

    @Test
    fun `a season moved onto another is refused`() {
        val autumn = autumn(2043)
        val spring = seasons.create("Spring 2043/44", LocalDate.of(2044, 2, 1), LocalDate.of(2044, 8, 31))

        assertThatThrownBy {
            seasons.update(spring.id!!, spring.name, autumn.startDate, autumn.endDate)
        }
            .isInstanceOf(SeasonOverlapException::class.java)
            .hasMessageContaining(autumn.name)
    }

    @Test
    fun `a season cannot end before it starts`() {
        assertThatThrownBy {
            seasons.create("Backwards", LocalDate.of(2045, 9, 1), LocalDate.of(2045, 8, 31))
        }.isInstanceOf(SeasonDatesReversedException::class.java)
    }
}
