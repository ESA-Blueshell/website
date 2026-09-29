package net.blueshell.api.board.domain

import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.board.persistence.BoardMemberRepository
import net.blueshell.api.board.persistence.BoardRepository
import net.blueshell.api.file.api.shippedPicturesOf
import net.blueshell.api.file.persistence.File
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.Optional

class ShippedBoardArtTest {
    private val fresh = mock<File> { on { id } doReturn 1L }
    private val taken = mock<File> { on { id } doReturn 2L }
    private val chosen = Entities.file()

    private val ninth = board(9, 90L)
    private val eighth = board(8, 80L, picture = chosen)
    private val seventh = board(7, 70L)
    private val sixth = board(6, 60L)

    private val roos = member(1L, "Roos")
    private val mo = member(2L, "Mo", picture = chosen)
    private val nel = member(3L, "Nel")
    private val kim = member(4L, "Kim")

    private val boards =
        mock<BoardRepository> {
            on { findByNumber(any()) } doReturn Optional.empty()
            on { findByNumber(9) } doReturn Optional.of(ninth)
            on { findByNumber(8) } doReturn Optional.of(eighth)
            on { findByNumber(7) } doReturn Optional.of(seventh)
            on { findByPictureId(1L) } doReturn Optional.empty()
            on { findByPictureId(2L) } doReturn Optional.of(sixth)
        }
    private val members =
        mock<BoardMemberRepository> {
            on { findByBoardId(90L) } doReturn listOf(roos, mo, nel)
            on { findByPictureId(1L) } doReturn Optional.empty()
            on { findByPictureId(2L) } doReturn Optional.of(kim)
        }

    private val art =
        ShippedBoardArt(
            shippedPicturesOf(
                mapOf(
                    "boards.csv" to listOf("9", "8", "7", "1").map { mapOf("number" to it) },
                    "members.csv" to
                        listOf("Roos", "Mo", "Nel", "Gone").map { mapOf("board" to "9", "name" to it) } +
                        mapOf("board" to "1", "name" to "Roos"),
                ),
            ) { row -> if (row["number"] == "7" || row["name"] == "Nel") taken else fresh },
            boards,
            members,
        )

    @Test
    fun `puts a photograph and a portrait on the board and member that have none`() {
        assertThat(art.apply()).isEqualTo(ShippedBoardArt.Applied(photos = 1, portraits = 1))
        verify(ninth).replacePicture(fresh)
        verify(boards).save(ninth)
        verify(roos).replacePicture(fresh)
        verify(members).save(roos)
    }

    @Test
    fun `leaves a chosen picture, one another record holds, and a record that is gone`() {
        art.onReady()

        verify(eighth, never()).replacePicture(any())
        verify(seventh, never()).replacePicture(any())
        verify(mo, never()).replacePicture(any())
        verify(nel, never()).replacePicture(any())
    }

    private fun board(
        number: Int,
        id: Long,
        picture: File? = null,
    ) = mock<Board> {
        on { this.number } doReturn number
        on { this.id } doReturn id
        on { this.picture } doReturn picture
    }

    private fun member(
        id: Long,
        name: String,
        picture: File? = null,
    ) = mock<BoardMember> {
        on { this.id } doReturn id
        on { displayName } doReturn name
        on { this.picture } doReturn picture
    }
}
