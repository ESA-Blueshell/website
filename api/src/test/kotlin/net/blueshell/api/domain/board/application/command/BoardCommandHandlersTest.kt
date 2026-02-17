package net.blueshell.api.domain.board.application.command

import net.blueshell.api.domain.board.application.BoardMemberService
import net.blueshell.api.domain.board.application.BoardService
import net.blueshell.api.domain.board.application.exception.BoardMemberNotFoundException
import net.blueshell.api.domain.board.command.AddBoardMemberCommand
import net.blueshell.api.domain.board.command.CreateBoardCommand
import net.blueshell.api.domain.board.command.RemoveBoardMemberCommand
import net.blueshell.api.domain.board.command.UpdateBoardCommand
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

class BoardCommandHandlersTest {

    private val boardService = mock<BoardService>()
    private val fileService = mock<FileService>()
    private val userService = mock<UserService>()
    private val boardMemberService = mock<BoardMemberService>()

    @Nested
    inner class CreateBoard {

        private val handler = CreateBoardHandler(boardService, fileService)

        @Test
        fun `creates board without picture`() {
            val command = CreateBoardCommand(
                name = "Board 2026",
                candidate = "Candidate",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 12, 31),
                pictureId = null
            )
            val boardCaptor = argumentCaptor<Board>()
            whenever(boardService.create(boardCaptor.capture())).thenAnswer { boardCaptor.firstValue }

            val result = handler.handle(command)

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
            val command = CreateBoardCommand(
                name = "Board 2026",
                candidate = "Candidate",
                startDate = LocalDate.of(2026, 1, 1),
                pictureId = 55L
            )
            val boardCaptor = argumentCaptor<Board>()
            whenever(fileService.findById(55L)).thenReturn(picture)
            whenever(boardService.create(boardCaptor.capture())).thenAnswer { boardCaptor.firstValue }

            val result = handler.handle(command)

            assertThat(result.picture).isSameAs(picture)
            verify(fileService).findById(55L)
        }
    }

    @Nested
    inner class UpdateBoard {

        private val handler = UpdateBoardHandler(boardService, fileService)

        @Test
        fun `updates board and clears picture when picture id is null`() {
            val board = boardEntity()
            board.replacePicture(mock())
            whenever(boardService.findById(7L)).thenReturn(board)
            whenever(boardService.update(board)).thenReturn(board)

            val result = handler.handle(
                UpdateBoardCommand(
                    id = 7L,
                    name = "Updated Board",
                    candidate = "Updated Candidate",
                    startDate = LocalDate.of(2026, 2, 1),
                    endDate = LocalDate.of(2026, 10, 1),
                    pictureId = null,
                    version = 3L
                )
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

            val result = handler.handle(
                UpdateBoardCommand(
                    id = 7L,
                    name = "Updated Board",
                    candidate = "Updated Candidate",
                    startDate = LocalDate.of(2026, 2, 1),
                    endDate = null,
                    pictureId = 21L,
                    version = 2L
                )
            )

            assertThat(result.picture).isSameAs(picture)
            verify(fileService).findById(21L)
        }
    }

    @Nested
    inner class AddBoardMember {

        private val handler = AddBoardMemberHandler(boardService, userService, boardMemberService)

        @Test
        fun `creates board member when membership does not exist`() {
            val board = boardEntity()
            val user = userEntity()
            whenever(boardMemberService.existsById(BoardMember.Id(9L, 11L))).thenReturn(false)
            whenever(boardService.findById(9L)).thenReturn(board)
            whenever(userService.findById(11L)).thenReturn(user)
            val memberCaptor = argumentCaptor<BoardMember>()
            whenever(boardMemberService.create(memberCaptor.capture())).thenAnswer { memberCaptor.firstValue }

            val result = handler.handle(
                AddBoardMemberCommand(
                    boardId = 9L,
                    userId = 11L,
                    role = "CHAIR",
                    startDate = LocalDate.of(2026, 1, 1),
                    endDate = null
                )
            )

            assertThat(result.role).isEqualTo("CHAIR")
            assertThat(result.startDate).isEqualTo(LocalDate.of(2026, 1, 1))
            verify(boardMemberService, never()).update(any())
        }

        @Test
        fun `updates existing board member when membership already exists`() {
            val board = boardEntity()
            val user = userEntity()
            val existing = BoardMember(
                board = board,
                user = user,
                role = "MEMBER",
                startDate = LocalDate.of(2025, 1, 1)
            )
            whenever(boardMemberService.existsById(BoardMember.Id(9L, 11L))).thenReturn(true)
            whenever(boardService.findById(9L)).thenReturn(board)
            whenever(userService.findById(11L)).thenReturn(user)
            whenever(boardMemberService.findById(BoardMember.Id(9L, 11L))).thenReturn(existing)
            whenever(boardMemberService.update(existing)).thenReturn(existing)

            val result = handler.handle(
                AddBoardMemberCommand(
                    boardId = 9L,
                    userId = 11L,
                    role = "TREASURER",
                    startDate = LocalDate.of(2026, 1, 1),
                    endDate = LocalDate.of(2026, 12, 31)
                )
            )

            assertThat(result.role).isEqualTo("TREASURER")
            assertThat(result.startDate).isEqualTo(LocalDate.of(2026, 1, 1))
            assertThat(result.endDate).isEqualTo(LocalDate.of(2026, 12, 31))
            verify(boardMemberService, never()).create(any())
        }
    }

    @Nested
    inner class RemoveBoardMember {

        private val handler = RemoveBoardMemberHandler(boardService, boardMemberService)

        @Test
        fun `deletes existing board member by id`() {
            whenever(boardService.findById(4L)).thenReturn(boardEntity())
            whenever(boardMemberService.existsById(BoardMember.Id(4L, 8L))).thenReturn(true)

            handler.handle(RemoveBoardMemberCommand(boardId = 4L, userId = 8L))

            verify(boardMemberService).deleteById(eq(BoardMember.Id(4L, 8L)))
        }

        @Test
        fun `throws when board member does not exist`() {
            whenever(boardService.findById(4L)).thenReturn(boardEntity())
            whenever(boardMemberService.existsById(BoardMember.Id(4L, 8L))).thenReturn(false)

            assertThrows<BoardMemberNotFoundException> {
                handler.handle(RemoveBoardMemberCommand(boardId = 4L, userId = 8L))
            }

            verify(boardMemberService, never()).deleteById(any())
        }
    }

    private fun boardEntity(): Board = Board().apply {
        name = "Board"
        candidate = "Candidate"
        startDate = LocalDate.of(2026, 1, 1)
    }

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
