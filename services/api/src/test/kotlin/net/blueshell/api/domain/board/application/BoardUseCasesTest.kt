package net.blueshell.api.domain.board.application

import net.blueshell.api.domain.board.application.exception.BoardMemberNotFoundException
import net.blueshell.api.domain.board.persistence.Board
import net.blueshell.api.domain.board.persistence.BoardMember
import net.blueshell.api.domain.file.application.FileService
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class BoardUseCasesTest {

    private val boardService = mock<BoardService>()
    private val fileService = mock<FileService>()
    private val userService = mock<UserService>()
    private val boardMemberService = mock<BoardMemberService>()
    private val useCases = BoardUseCases(boardService, boardMemberService, fileService, userService)

    @Nested
    inner class CreateBoard {


        @Test
        fun `creates board without picture`() {
            val boardCaptor = argumentCaptor<Board>()
            whenever(boardService.create(boardCaptor.capture())).thenAnswer { boardCaptor.firstValue }

            val result = useCases.create(
                name = "Board 2026",
                candidate = "Candidate",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 12, 31),
                pictureId = null,
            )

            assertThat(result.name).isEqualTo("Board 2026")
            assertThat(result.candidate).isEqualTo("Candidate")
            assertThat(result.startDate).isEqualTo(LocalDate.of(2026, 1, 1))
            assertThat(result.endDate).isEqualTo(LocalDate.of(2026, 12, 31))
            assertThat(result.picture).isNull()
            verify(fileService, never()).findById(any())
        }

        @Test
        fun `attaches picture when picture id is provided`() {
            val picture = mock<File>()
            val boardCaptor = argumentCaptor<Board>()
            whenever(fileService.findById(55L)).thenReturn(picture)
            whenever(boardService.create(boardCaptor.capture())).thenAnswer { boardCaptor.firstValue }

            val result = useCases.create(
                name = "Board 2026",
                candidate = "Candidate",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = null,
                pictureId = 55L,
            )

            assertThat(result.picture).isSameAs(picture)
            verify(fileService).findById(55L)
        }
    }

    @Nested
    inner class UpdateBoard {


        @Test
        fun `updates board and clears picture when picture id is null`() {
            val board = boardEntity()
            board.replacePicture(mock())
            whenever(boardService.findById(7L)).thenReturn(board)
            whenever(boardService.update(board)).thenReturn(board)

            val result = useCases.update(
                    id = 7L,
                    name = "Updated Board",
                    candidate = "Updated Candidate",
                    startDate = LocalDate.of(2026, 2, 1),
                    endDate = LocalDate.of(2026, 10, 1),
                    pictureId = null,
                )

            assertThat(result.name).isEqualTo("Updated Board")
            assertThat(result.candidate).isEqualTo("Updated Candidate")
            assertThat(result.picture).isNull()
            verify(fileService, never()).findById(any())
        }

        @Test
        fun `updates board and replaces picture when picture id is provided`() {
            val board = boardEntity()
            val picture = mock<File>()
            whenever(boardService.findById(7L)).thenReturn(board)
            whenever(fileService.findById(21L)).thenReturn(picture)
            whenever(boardService.update(board)).thenReturn(board)

            val result = useCases.update(
                    id = 7L,
                    name = "Updated Board",
                    candidate = "Updated Candidate",
                    startDate = LocalDate.of(2026, 2, 1),
                    endDate = null,
                    pictureId = 21L,
                )

            assertThat(result.picture).isSameAs(picture)
            verify(fileService).findById(21L)
        }
    }

    @Nested
    inner class AddBoardMember {

        @Test
        fun `seats a member who is not on the board yet`() {
            val board = boardEntity()
            val user = userEntity()
            whenever(boardService.findById(9L)).thenReturn(board)
            whenever(userService.findById(11L)).thenReturn(user)
            whenever(boardMemberService.findByBoardAndUser(eq(9L), any())).thenReturn(null)
            val memberCaptor = argumentCaptor<BoardMember>()
            whenever(boardMemberService.create(memberCaptor.capture())).thenAnswer { memberCaptor.firstValue }

            val result = useCases.addMember(
                boardId = 9L,
                userId = 11L,
                role = "CHAIR",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = null,
            )

            assertThat(result.role).isEqualTo("CHAIR")
            assertThat(result.startDate).isEqualTo(LocalDate.of(2026, 1, 1))
            verify(boardMemberService, never()).update(any())
        }

        @Test
        fun `seats somebody with no account under their own name`() {
            whenever(boardService.findById(9L)).thenReturn(boardEntity())
            val memberCaptor = argumentCaptor<BoardMember>()
            whenever(boardMemberService.create(memberCaptor.capture())).thenAnswer { memberCaptor.firstValue }

            val result = useCases.addMember(
                boardId = 9L,
                userId = null,
                role = "CHAIR",
                startDate = LocalDate.of(2018, 9, 1),
                endDate = null,
                displayName = "Thijs Lieverse",
                description = "The first chair.",
            )

            assertThat(result.user).isNull()
            assertThat(result.displayName).isEqualTo("Thijs Lieverse")
            assertThat(result.name).isEqualTo("Thijs Lieverse")
            // Nobody to look up, so no account is fetched to seat them.
            verify(userService, never()).findById(any())
        }

        @Test
        fun `updates the seat a member already holds on that board`() {
            val board = boardEntity()
            val user = userEntity()
            val existing = BoardMember(
                board = board,
                user = user,
                role = "MEMBER",
                startDate = LocalDate.of(2025, 1, 1)
            )
            whenever(boardService.findById(9L)).thenReturn(board)
            whenever(userService.findById(11L)).thenReturn(user)
            whenever(boardMemberService.findByBoardAndUser(eq(9L), any())).thenReturn(existing)
            whenever(boardMemberService.update(existing)).thenReturn(existing)

            val result = useCases.addMember(
                boardId = 9L,
                userId = 11L,
                role = "TREASURER",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 12, 31),
            )

            assertThat(result.role).isEqualTo("TREASURER")
            assertThat(result.startDate).isEqualTo(LocalDate.of(2026, 1, 1))
            assertThat(result.endDate).isEqualTo(LocalDate.of(2026, 12, 31))
            verify(boardMemberService, never()).create(any())
        }
    }

    @Nested
    inner class LinkBoardMember {

        @Test
        fun `attaches a member to a seat that had none`() {
            val seat = seatEntity()
            val user = userEntity()
            whenever(boardMemberService.findSeat(3L)).thenReturn(seat)
            whenever(userService.findById(11L)).thenReturn(user)
            whenever(boardMemberService.update(seat)).thenReturn(seat)

            val result = useCases.linkMember(3L, 11L)

            assertThat(result.user).isSameAs(user)
        }

        @Test
        fun `detaches a seat, which keeps standing under its own name`() {
            val seat = seatEntity().apply {
                user = userEntity()
                displayName = "Thijs Lieverse"
            }
            whenever(boardMemberService.findSeat(3L)).thenReturn(seat)
            whenever(boardMemberService.update(seat)).thenReturn(seat)

            val result = useCases.linkMember(3L, null)

            assertThat(result.user).isNull()
            assertThat(result.name).isEqualTo("Thijs Lieverse")
            verify(userService, never()).findById(any())
        }
    }

    @Nested
    inner class RemoveBoardMember {

        @Test
        fun `deletes the seat by its own id`() {
            whenever(boardMemberService.findSeat(4L)).thenReturn(seatEntity())

            useCases.removeMember(4L)

            verify(boardMemberService).deleteById(eq(4L))
        }

        @Test
        fun `throws when the seat does not exist`() {
            whenever(boardMemberService.findSeat(4L)).thenThrow(BoardMemberNotFoundException(4L))

            assertThrows<BoardMemberNotFoundException> {
                useCases.removeMember(4L)
            }

            verify(boardMemberService, never()).deleteById(any())
        }
    }

    private fun seatEntity(): BoardMember = BoardMember(
        board = boardEntity(),
        role = "CHAIR",
        startDate = LocalDate.of(2018, 9, 1),
    )

    private fun boardEntity(): Board = Board(
        candidate = "Candidate",
        startDate = LocalDate.of(2026, 1, 1),
        name = "Board",
    )

    private fun userEntity(): User = User(
        username = "member_${System.nanoTime()}",
        email = "member_${System.nanoTime()}@example.com",
        password = "secret",
        initials = "TU",
        firstName = "Test",
        lastName = "User",
        phoneNumber = "+31612345678",
        discord = "member#1234"
    ).apply {
        roles = mutableSetOf(Role.MEMBER)
        enabled = true
    }
}
