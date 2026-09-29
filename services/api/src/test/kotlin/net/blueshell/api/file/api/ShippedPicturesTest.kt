package net.blueshell.api.file.api

import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.testsupport.EsportsSeedFixture
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

class ShippedPicturesTest {
    private val stored = mutableMapOf<String, File>()
    private val files =
        mock<FileService> {
            on { store(any(), any(), any(), any(), any()) } doAnswer { call ->
                val name = call.getArgument<String>(1)
                Entities.file(path = "art/$name").also { stored[it.path] = it }
            }
            on { findPublicImage(any(), eq(FileType.TEAM_BANNER)) } doAnswer { stored[it.getArgument(0)] }
        }
    private val users = mock<UserService> { on { findByUsername("system") } doReturn Entities.user() }
    private val transactions =
        mock<TransactionTemplate> {
            on { execute(any<TransactionCallback<Boolean>>()) } doAnswer {
                it.getArgument<TransactionCallback<Boolean>>(0).doInTransaction(mock())
            }
        }
    private val pictures = ShippedPictures(files, users, transactions)

    /** The fixture's teams: Nomads twice and Settlers once name art, Settlers sharing Nomads's; Drifters names none. */
    private fun ship(place: (Map<String, String>, () -> File) -> Boolean) =
        pictures.ship(EsportsSeedFixture.files, "teams.csv", "banner", FileType.TEAM_BANNER, place)

    @Test
    fun `stores every picture once before placing any, and hands each row its own`() {
        val seen = mutableListOf<Pair<String, String>>()

        val placed =
            ship { row, picture ->
                assertThat(stored.keys).describedAs("stored before the first row is placed").hasSize(2)
                seen += row.getValue("name") to picture().path
                true
            }

        assertThat(placed).isEqualTo(3)
        assertThat(stored.keys).containsExactlyInAnyOrder("art/team-art.webp", "art/beta-team-art.webp")
        assertThat(seen).containsExactly(
            "Nomads" to "art/team-art.webp",
            "Nomads" to "art/beta-team-art.webp",
            "Settlers" to "art/team-art.webp",
        )
    }

    @Test
    fun `counts only the rows the loader placed a picture for`() {
        assertThat(ship { row, _ -> row.getValue("name") == "Settlers" }).isEqualTo(1)
    }

    @Test
    fun `skips a row that fails, and places the rest`() {
        val placed =
            ship { row, _ ->
                check(row.getValue("name") != "Settlers") { "no" }
                true
            }

        assertThat(placed).isEqualTo(2)
    }

    @Test
    fun `places nothing when there is no account to credit the art to`() {
        val nobody = ShippedPictures(files, mock<UserService>(), transactions)

        assertThat(nobody.ship(EsportsSeedFixture.files, "teams.csv", "banner", FileType.TEAM_BANNER) { _, _ -> true }).isZero()
        verifyNoInteractions(files)
    }

    @Test
    fun `answers nothing placed where the seed file is missing, rather than failing the start`() {
        assertThat(pictures.ship(EsportsSeedFixture.files, "nowhere.csv", "banner", FileType.TEAM_BANNER) { _, _ -> true }).isZero()
    }
}
