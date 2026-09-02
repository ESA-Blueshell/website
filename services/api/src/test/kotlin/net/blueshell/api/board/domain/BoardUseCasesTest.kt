package net.blueshell.api.board.domain

import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
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
import net.blueshell.api.board.api.BoardMemberService

class BoardUseCasesTest {

    private val boardService = mock<BoardService>()
    private val pictures = mock<StoredPictures>()
    private val userService = mock<UserService>()
    private val boardMemberService = mock<BoardMemberService>()
    private val useCases = BoardUseCases(boardService, boardMemberService, pictures, userService)

    @Nested
    inner class CreateBoard {


        @Test
        fun `creates board without picture`() {
            val boardCaptor = argumentCaptor<Board>()
            whenever(boardService.create(boardCaptor.capture())).thenAnswer { boardCaptor.firstValue }

            val result = useCases.create(
                number = 10,
                name = "Board 2026",
                candidate = "Candidate",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 12, 31),
                photo = null,
            )

            assertThat(result.number).isEqualTo(10)
            assertThat(result.name).isEqualTo("Board 2026")
            assertThat(result.candidate).isEqualTo("Candidate")
            assertThat(result.startDate).isEqualTo(LocalDate.of(2026, 1, 1))
            assertThat(result.endDate).isEqualTo(LocalDate.of(2026, 12, 31))
            assertThat(result.picture).isNull()
        }

        @Test
        fun `a board with no name of its own keeps something in the column that duplicates it`() {
            val boardCaptor = argumentCaptor<Board>()
            whenever(boardService.create(boardCaptor.capture())).thenAnswer { boardCaptor.firstValue }

            val result = useCases.create(
                number = 4,
                name = "",
                candidate = null,
                startDate = LocalDate.of(2020, 9, 1),
                endDate = LocalDate.of(2021, 8, 31),
                photo = null,
            )

            // A board is free to have no name recorded, and `candidate` is NOT NULL.
            assertThat(result.name).isNull()
            assertThat(result.candidate).isEqualTo("Board 4")
        }

        @Test
        fun `refuses a number another board already holds`() {
            whenever(boardService.findByNumber(9)).thenReturn(boardEntity())

            assertThrows<DuplicateBoardException> {
                useCases.create(
                    number = 9,
                    name = "Eeveelutions",
                    candidate = null,
                    startDate = LocalDate.of(2025, 9, 1),
                    endDate = null,
                    photo = null,
                )
            }

            verify(boardService, never()).create(any())
        }

        @Test
        fun `takes the photograph a stored path names`() {
            val picture = mock<File>()
            val boardCaptor = argumentCaptor<Board>()
            whenever(pictures.of(PHOTO_PATH, FileType.BOARD_PHOTO)).thenReturn(picture)
            whenever(boardService.create(boardCaptor.capture())).thenAnswer { boardCaptor.firstValue }

            val result = useCases.create(
                number = 10,
                name = "Board 2026",
                candidate = "Candidate",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = null,
                photo = PHOTO_PATH,
            )

            assertThat(result.picture).isSameAs(picture)
        }
    }

    @Nested
    inner class UpdateBoard {


        @Test
        fun `updates board and clears the photograph when none is named`() {
            val board = boardEntity()
            board.replacePicture(mock())
            whenever(boardService.findById(7L)).thenReturn(board)
            whenever(boardService.update(board)).thenReturn(board)

            val result = useCases.update(
                    id = 7L,
                    number = 10,
                    name = "Updated Board",
                    candidate = "Updated Candidate",
                    startDate = LocalDate.of(2026, 2, 1),
                    endDate = LocalDate.of(2026, 10, 1),
                    photo = null,
                )

            assertThat(result.name).isEqualTo("Updated Board")
            assertThat(result.candidate).isEqualTo("Updated Candidate")
            assertThat(result.picture).isNull()
        }

        @Test
        fun `updates board and replaces the photograph a stored path names`() {
            val board = boardEntity()
            val picture = mock<File>()
            whenever(boardService.findById(7L)).thenReturn(board)
            whenever(pictures.of(PHOTO_PATH, FileType.BOARD_PHOTO)).thenReturn(picture)
            whenever(boardService.update(board)).thenReturn(board)

            val result = useCases.update(
                    id = 7L,
                    number = 10,
                    name = "Updated Board",
                    candidate = "Updated Candidate",
                    startDate = LocalDate.of(2026, 2, 1),
                    endDate = null,
                    photo = PHOTO_PATH,
                )

            assertThat(result.picture).isSameAs(picture)
        }
    }

    @Nested
    inner class AddBoardMember {

        @Test
        fun `adds a member who is not on the board yet`() {
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
        fun `adds somebody with no account under their own name`() {
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
            // Nobody to look up, so no account is fetched to add them.
            verify(userService, never()).findById(any())
        }

        @Test
        fun `records the nickname beside the name rather than inside it`() {
            whenever(boardService.findById(9L)).thenReturn(boardEntity())
            val memberCaptor = argumentCaptor<BoardMember>()
            whenever(boardMemberService.create(memberCaptor.capture())).thenAnswer { memberCaptor.firstValue }

            val result = useCases.addMember(
                boardId = 9L,
                userId = null,
                role = "Commissioner of Internal Affairs",
                startDate = LocalDate.of(2022, 9, 1),
                endDate = null,
                displayName = "Roos Kruk",
                nickname = "SkyeWolf",
            )

            assertThat(result.displayName).isEqualTo("Roos Kruk")
            assertThat(result.nickname).isEqualTo("SkyeWolf")
        }

        @Test
        fun `takes the portrait a stored path names`() {
            val portrait = mock<File>()
            whenever(boardService.findById(9L)).thenReturn(boardEntity())
            whenever(pictures.of(PORTRAIT_PATH, FileType.BOARD_PORTRAIT)).thenReturn(portrait)
            val memberCaptor = argumentCaptor<BoardMember>()
            whenever(boardMemberService.create(memberCaptor.capture())).thenAnswer { memberCaptor.firstValue }

            val result = useCases.addMember(
                boardId = 9L,
                userId = null,
                role = "Chair",
                startDate = LocalDate.of(2022, 9, 1),
                endDate = null,
                displayName = "Amber Scholtz",
                portrait = PORTRAIT_PATH,
            )

            assertThat(result.picture).isSameAs(portrait)
        }

        @Test
        fun `a membership somebody already holds takes the portrait too`() {
            val board = boardEntity()
            val user = userEntity()
            val portrait = mock<File>()
            val existing = BoardMember(
                board = board,
                user = user,
                role = "MEMBER",
                startDate = LocalDate.of(2025, 1, 1),
            )
            whenever(boardService.findById(9L)).thenReturn(board)
            whenever(userService.findById(11L)).thenReturn(user)
            whenever(boardMemberService.findByBoardAndUser(eq(9L), any())).thenReturn(existing)
            whenever(pictures.of(PORTRAIT_PATH, FileType.BOARD_PORTRAIT)).thenReturn(portrait)
            whenever(boardMemberService.update(existing)).thenReturn(existing)

            val result = useCases.addMember(
                boardId = 9L,
                userId = 11L,
                role = "TREASURER",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = null,
                portrait = PORTRAIT_PATH,
            )

            assertThat(result.picture).isSameAs(portrait)
        }

        @Test
        fun `updates the membership an account already holds on that board`() {
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
    inner class UpdateBoardMember {

        @Test
        fun `a corrected member keeps the portrait it is given and loses one it is not`() {
            val member = memberEntity()
            val portrait = mock<File>()
            whenever(boardMemberService.findMember(3L)).thenReturn(member)
            whenever(pictures.of(PORTRAIT_PATH, FileType.BOARD_PORTRAIT)).thenReturn(portrait)
            whenever(boardMemberService.update(member)).thenReturn(member)

            useCases.updateMember(
                id = 3L,
                role = "Chair",
                startDate = LocalDate.of(2022, 9, 1),
                endDate = null,
                portrait = PORTRAIT_PATH,
            )
            assertThat(member.picture).isSameAs(portrait)

            // Nothing named, so nothing held: the save carries the whole member every time.
            useCases.updateMember(
                id = 3L,
                role = "Chair",
                startDate = LocalDate.of(2022, 9, 1),
                endDate = null,
            )
            assertThat(member.picture).isNull()
        }
    }

    @Nested
    inner class LinkBoardMember {

        @Test
        fun `attaches an account to a member who had none`() {
            val member = memberEntity()
            val user = userEntity()
            whenever(boardMemberService.findMember(3L)).thenReturn(member)
            whenever(userService.findById(11L)).thenReturn(user)
            whenever(boardMemberService.update(member)).thenReturn(member)

            val result = useCases.linkMember(3L, 11L)

            assertThat(result.user).isSameAs(user)
        }

        @Test
        fun `detaches a member, which keeps standing under their own name`() {
            val member = memberEntity().apply {
                user = userEntity()
                displayName = "Thijs Lieverse"
            }
            whenever(boardMemberService.findMember(3L)).thenReturn(member)
            whenever(boardMemberService.update(member)).thenReturn(member)

            val result = useCases.linkMember(3L, null)

            assertThat(result.user).isNull()
            assertThat(result.name).isEqualTo("Thijs Lieverse")
            verify(userService, never()).findById(any())
        }
    }

    @Nested
    inner class RemoveBoardMember {

        @Test
        fun `deletes the member by their own id`() {
            whenever(boardMemberService.findMember(4L)).thenReturn(memberEntity())

            useCases.removeMember(4L)

            verify(boardMemberService).deleteById(eq(4L))
        }

        @Test
        fun `throws when the member does not exist`() {
            whenever(boardMemberService.findMember(4L)).thenThrow(BoardMemberNotFoundException(4L))

            assertThrows<BoardMemberNotFoundException> {
                useCases.removeMember(4L)
            }

            verify(boardMemberService, never()).deleteById(any())
        }
    }

    private companion object {
        const val PHOTO_PATH = "board-photos/abc.webp"
        const val PORTRAIT_PATH = "board-portraits/def.webp"
    }

    private fun memberEntity(): BoardMember = BoardMember(
        board = boardEntity(),
        role = "CHAIR",
        startDate = LocalDate.of(2018, 9, 1),
    )

    private fun boardEntity(): Board = Board(
        number = 10,
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
