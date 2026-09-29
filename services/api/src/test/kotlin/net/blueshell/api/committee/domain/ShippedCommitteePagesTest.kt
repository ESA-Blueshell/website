package net.blueshell.api.committee.domain

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeRepository
import net.blueshell.api.game.api.ShippedGames
import net.blueshell.api.shared.seed.SeedCsv
import net.blueshell.api.shared.seed.SeedDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ShippedCommitteePagesTest {
    private val db = SeedDatabase()
    private val held = mutableMapOf<String, Committee>()
    private val standing = mutableSetOf<String>()

    private val committees =
        mock<CommitteeRepository>().also { mock ->
            whenever(mock.findByName(any())).thenAnswer { held[it.arguments[0]] }
        }
    private val games = mock<ShippedGames>().also { mock -> whenever(mock.stands(any())).thenAnswer { it.arguments[0] in standing } }
    private val pages = ShippedCommitteePages(committees, games, db.dataSource, db.transactions, SeedCsv("db/seed/committee-fixtures"))

    private fun committee(name: String) = Committee(name = name, description = "").also { held[name] = it }

    @Test
    fun `links a committee's games once, and the board's later edits outlive the next run`() {
        val lanCie = committee("LanCie")
        standing += listOf("ALPHA", "BETA")

        assertThat(pages.apply()).isEqualTo(2)
        assertThat(lanCie.gameCodes).containsExactly("ALPHA", "BETA")

        lanCie.gameCodes.remove("BETA")

        assertThat(pages.apply()).isZero()
        assertThat(lanCie.gameCodes).containsExactly("ALPHA")
    }

    @Test
    fun `waits for a committee or a game that is not standing yet, and records a link already there`() {
        val lanCie = committee("LanCie")
        standing += "ALPHA"
        lanCie.gameCodes.add("ALPHA")

        assertThat(pages.apply()).isZero()

        standing += "BETA"
        assertThat(pages.apply()).isEqualTo(1)
        assertThat(lanCie.gameCodes).containsExactly("ALPHA", "BETA")
    }

    @Test
    fun `reads the file that ships where it is given none`() {
        assertThat(ShippedCommitteePages(committees, games, db.dataSource, db.transactions).apply()).isZero()
    }

    @Test
    fun `a failing run never stops the start`() {
        val failing = mock<ShippedCommitteePages> { on { apply() } doThrow IllegalStateException("down") }
        ShippedCommitteePagesOnStartup(failing).onReady()

        val lanCie = committee("LanCie")
        standing += "ALPHA"
        ShippedCommitteePagesOnStartup(pages).onReady()
        assertThat(lanCie.gameCodes).containsExactly("ALPHA")
    }
}
