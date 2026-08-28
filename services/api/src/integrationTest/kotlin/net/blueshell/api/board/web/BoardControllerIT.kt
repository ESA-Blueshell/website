package net.blueshell.api.board.web

import net.blueshell.api.board.persistence.BoardMemberRepository
import net.blueshell.api.board.persistence.BoardRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
class BoardControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var boardRepository: BoardRepository

    @Autowired
    private lateinit var boardMemberRepository: BoardMemberRepository

    private fun createBoardPayload(
        name: String = "Board ${System.currentTimeMillis()}",
        startDate: LocalDate = LocalDate.now().minusDays(1)
    ): String =
        """{"name":"$name","candidate":"Candidate","startDate":"$startDate"}"""

    private fun updateBoardPayload(
        version: Long,
        name: String = "Updated Board ${System.currentTimeMillis()}",
        startDate: LocalDate = LocalDate.now().minusDays(2)
    ): String =
        """{"name":"$name","candidate":"Updated Candidate","startDate":"$startDate","version":$version}"""

    private fun addMemberPayload(
        userId: Long,
        role: String = "CHAIR",
        startDate: LocalDate = LocalDate.now().minusDays(1)
    ): String =
        """{"userId":$userId,"role":"$role","startDate":"$startDate"}"""

    @Nested
    inner class CreateBoard {

        @Test
        fun `creates board`() {
            val boardUser = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/boards")
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBoardPayload())
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNumber)
                .andExpect(jsonPath("$.candidate").value("Candidate"))
                .andExpect(jsonPath("$.members").isArray)
        }

        @Test
        fun `returns bad request for invalid create payload`() {
            val boardUser = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/boards")
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"","candidate":"Candidate","startDate":"${LocalDate.now().minusDays(1)}"}""")
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class FindAllBoards {

        @Test
        fun `lists boards`() {
            createBoardFixture()

            mvc.perform(get("/boards"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$[0].id").isNumber)
        }
    }

    @Nested
    inner class FindBoardById {

        @Test
        fun `finds board by id`() {
            val board = createBoardFixture()

            mvc.perform(get("/boards/{id}", board.id))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(board.id))
                .andExpect(jsonPath("$.name").value(board.name))
                .andExpect(jsonPath("$.candidate").value(board.candidate))
        }

        @Test
        fun `returns not found when board does not exist`() {
            mvc.perform(get("/boards/{id}", 999999L))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class UpdateBoard {

        @Test
        fun `updates board`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val newName = "Board Updated ${System.currentTimeMillis()}"

            mvc.perform(
                put("/boards/{id}", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBoardPayload(board.version, newName))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(board.id))
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.candidate").value("Updated Candidate"))

            val updated = boardRepository.findById(board.id!!).orElseThrow()
            assertThat(updated.name).isEqualTo(newName)
            assertThat(updated.candidate).isEqualTo("Updated Candidate")
        }

        @Test
        fun `returns not found when board does not exist`() {
            val boardUser = createUserWithRole(Role.BOARD)

            mvc.perform(
                put("/boards/{id}", 999999L)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBoardPayload(0))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class DeleteBoard {

        @Test
        fun `deletes board`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()

            mvc.perform(
                delete("/boards/{id}", board.id)
                    .with(bearer(boardUser))
            )
                .andExpect(status().isNoContent)

            assertThat(boardRepository.existsById(board.id!!)).isFalse()
        }

        @Test
        fun `returns not found when deleting missing board`() {
            val boardUser = createUserWithRole(Role.BOARD)

            mvc.perform(
                delete("/boards/{id}", 999999L)
                    .with(bearer(boardUser))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class AddMember {

        @Test
        fun `adds board member`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/boards/{boardId}/members", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(addMemberPayload(user.id!!))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.boardId").value(board.id))
                .andExpect(jsonPath("$.userId").value(user.id))
                .andExpect(jsonPath("$.role").value("CHAIR"))

            assertThat(boardMemberRepository.findByBoardIdAndUserId(board.id!!, user.id!!)).isPresent
        }

        @Test
        fun `updates existing board member when same user is added again`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val user = createUserWithRole(Role.MEMBER)
            addBoardMember(board, user, role = "MEMBER")

            mvc.perform(
                post("/boards/{boardId}/members", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(addMemberPayload(user.id!!, role = "TREASURER"))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.role").value("TREASURER"))

            val member = boardMemberRepository.findByBoardIdAndUserId(board.id!!, user.id!!).orElseThrow()
            assertThat(member.role).isEqualTo("TREASURER")
        }

        @Test
        fun `returns not found when board does not exist`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/boards/{boardId}/members", 999999L)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(addMemberPayload(user.id!!))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class RemoveMember {

        @Test
        fun `removes a seat by its own id`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val user = createUserWithRole(Role.MEMBER)
            val seat = addBoardMember(board, user).members.first()

            mvc.perform(
                delete("/boards/{boardId}/members/{id}", board.id, seat.id)
                    .with(bearer(boardUser))
            )
                .andExpect(status().isNoContent)

            assertThat(boardMemberRepository.findById(seat.id!!)).isEmpty
        }

        @Test
        fun `returns not found when the seat does not exist`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()

            mvc.perform(
                delete("/boards/{boardId}/members/{id}", board.id, 999999L)
                    .with(bearer(boardUser))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class SeatsWithoutAccounts {

        @Test
        fun `seats somebody with no account, under their own name`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()

            mvc.perform(
                post("/boards/{boardId}/members", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"role":"Chair","startDate":"2017-09-01","endDate":"2018-08-31",
                         "displayName":"Thijs Lieverse","description":"The first chair.",
                         "image":"board1/thijs.jpg"}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.name").value("Thijs Lieverse"))
                .andExpect(jsonPath("$.description").value("The first chair."))
                .andExpect(jsonPath("$.image").value("board1/thijs.jpg"))
        }

        @Test
        fun `a linked seat is named by the member, not by what was recorded`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val user = createUserWithRole(Role.MEMBER)
            val seat = addBoardSeat(board, displayName = "Somebody Else")

            mvc.perform(
                put("/boards/{boardId}/members/{id}/member", board.id, seat.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\": ${user.id}}")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.userId").value(user.id))
                .andExpect(jsonPath("$.name").value(user.fullName))

            // Detaching leaves the seat standing under the name it was recorded with.
            mvc.perform(
                put("/boards/{boardId}/members/{id}/member", board.id, seat.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.name").value("Somebody Else"))
        }

        @Test
        fun `a board carries its own photograph, and anybody may read it`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val seat = addBoardSeat(board, displayName = "Nobody Here")

            mvc.perform(
                put("/boards/{id}", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"${board.name}","candidate":"${board.candidate}",
                         "startDate":"${board.startDate}","image":"board1/board1.jpg",
                         "version":${board.version}}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.image").value("board1/board1.jpg"))

            // Anybody may read a board, which is what the public page does.
            mvc.perform(get("/boards/{id}", board.id))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.image").value("board1/board1.jpg"))
                .andExpect(jsonPath("$.members[?(@.id == %d)].name".format(seat.id)).value("Nobody Here"))
        }
    }
}
