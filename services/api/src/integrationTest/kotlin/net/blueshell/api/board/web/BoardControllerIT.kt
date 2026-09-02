package net.blueshell.api.board.web

import com.jayway.jsonpath.JsonPath
import net.blueshell.api.board.persistence.BoardMemberRepository
import net.blueshell.api.board.persistence.BoardRepository
import net.blueshell.api.file.api.FileService
import net.blueshell.api.shared.enums.FileType
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
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class BoardControllerIT : UserTestSupport() {

    private companion object {
        /** Numbers this suite's own payloads claim, well clear of the fixtures' own. */
        val numbers = AtomicInteger(5000)
    }

    @Autowired
    private lateinit var boardRepository: BoardRepository

    @Autowired
    private lateinit var boardMemberRepository: BoardMemberRepository

    @Autowired
    private lateinit var files: FileService

    /**
     * A picture in storage, the way an upload leaves one, so a save has a real path to name.
     *
     * Stored wide enough to be stored at more than one width: what the response is being asked
     * for is the ladder, and a picture narrower than the narrowest rung carries none.
     */
    private fun storedPicture(kind: FileType, width: Int = 800, height: Int = 600): String {
        val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val bytes = java.io.ByteArrayOutputStream()
            .also { javax.imageio.ImageIO.write(image, "png", it) }
            .toByteArray()
        return files.store(
            content = java.io.ByteArrayInputStream(bytes),
            originalName = "chosen-${System.nanoTime()}.png",
            declaredMediaType = "image/png",
            type = kind,
            uploader = createUserWithRole(Role.ADMIN),
        ).path
    }

    /** A number no fixture in this suite holds, so a payload never clashes with one. */
    private fun freeNumber(): Int = numbers.incrementAndGet()

    private fun createBoardPayload(
        name: String = "Board ${System.currentTimeMillis()}",
        startDate: LocalDate = LocalDate.now().minusDays(1),
        number: Int = freeNumber()
    ): String =
        """{"number":$number,"name":"$name","candidate":"Candidate","startDate":"$startDate"}"""

    private fun updateBoardPayload(
        version: Long,
        name: String = "Updated Board ${System.currentTimeMillis()}",
        startDate: LocalDate = LocalDate.now().minusDays(2),
        number: Int = freeNumber()
    ): String =
        """{"number":$number,"name":"$name","candidate":"Updated Candidate",""" +
            """"startDate":"$startDate","version":$version}"""

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

            // A board with no number is not a board: the number is what identifies one.
            mvc.perform(
                post("/boards")
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"number":0,"name":"Board","startDate":"${LocalDate.now().minusDays(1)}"}""")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `creates a board with its number, its cheer, its colour and its description`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val number = freeNumber()

            mvc.perform(
                post("/boards")
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"number":$number,"name":"Wasted","cheer":"RNG, Be With Me!",
                         "accent":"#7b2ff7","description":"The year of the lounge.",
                         "startDate":"2024-09-01","endDate":"2025-08-31"}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.number").value(number))
                .andExpect(jsonPath("$.name").value("Wasted"))
                .andExpect(jsonPath("$.cheer").value("RNG, Be With Me!"))
                .andExpect(jsonPath("$.accent").value("#7b2ff7"))
                .andExpect(jsonPath("$.description").value("The year of the lounge."))
                // Nothing reads `candidate`, and it is NOT NULL, so a write fills it.
                .andExpect(jsonPath("$.candidate").value("Wasted"))
        }

        @Test
        fun `creates a board that has no name of its own`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val number = freeNumber()

            mvc.perform(
                post("/boards")
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"number":$number,"startDate":"2017-09-01","endDate":"2018-08-31"}""")
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.candidate").value("Board $number"))
        }

        @Test
        fun `refuses a number a board already holds`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val taken = createBoardFixture().number

            mvc.perform(
                post("/boards")
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBoardPayload(number = taken))
            )
                .andExpect(status().isConflict)
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
        fun `corrects the number, the cheer, the colour and the description`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val number = freeNumber()

            mvc.perform(
                put("/boards/{id}", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"number":$number,"name":"Overcooked","cheer":"Krijg de tering!",
                         "accent":"#12a4a4","description":"The seventh year.",
                         "startDate":"2023-09-01","endDate":"2024-08-31","version":${board.version}}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.number").value(number))
                .andExpect(jsonPath("$.cheer").value("Krijg de tering!"))
                .andExpect(jsonPath("$.accent").value("#12a4a4"))
                .andExpect(jsonPath("$.description").value("The seventh year."))

            val updated = boardRepository.findById(board.id!!).orElseThrow()
            assertThat(updated.number).isEqualTo(number)
            assertThat(updated.cheer).isEqualTo("Krijg de tering!")
            assertThat(updated.accent).isEqualTo("#12a4a4")
            assertThat(updated.description).isEqualTo("The seventh year.")
        }

        @Test
        fun `a blank colour means the association's own, not an empty string`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()

            mvc.perform(
                put("/boards/{id}", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"number":${board.number},"name":"","cheer":"","accent":"","description":"",
                         "startDate":"${board.startDate}","version":${board.version}}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.cheer").doesNotExist())
                .andExpect(jsonPath("$.accent").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
        }

        @Test
        fun `refuses a number another board already holds`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val taken = createBoardFixture().number

            mvc.perform(
                put("/boards/{id}", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBoardPayload(board.version, number = taken))
            )
                .andExpect(status().isConflict)
        }

        @Test
        fun `keeps its own number rather than refusing itself`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()

            mvc.perform(
                put("/boards/{id}", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBoardPayload(board.version, number = board.number))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.number").value(board.number))
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
        fun `removes a member by their own id`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val user = createUserWithRole(Role.MEMBER)
            val member = addBoardMember(board, user).members.first()

            mvc.perform(
                delete("/boards/{boardId}/members/{id}", board.id, member.id)
                    .with(bearer(boardUser))
            )
                .andExpect(status().isNoContent)

            assertThat(boardMemberRepository.findById(member.id!!)).isEmpty
        }

        @Test
        fun `returns not found when the member does not exist`() {
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
    inner class MembersWithoutAccounts {

        @Test
        fun `adds somebody with no account, under their own name`() {
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
        fun `a member carries their nickname beside the name, and both round-trip`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()

            val created = mvc.perform(
                post("/boards/{boardId}/members", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"role":"Commissioner of Internal Affairs","startDate":"2022-09-01",
                         "displayName":"Roos Kruk","nickname":"SkyeWolf"}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("Roos Kruk"))
                .andExpect(jsonPath("$.nickname").value("SkyeWolf"))
                .andReturn()

            val memberId = JsonPath.read<Int>(created.response.contentAsString, "$.id")

            mvc.perform(
                put("/boards/{boardId}/members/{id}", board.id, memberId)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"role":"Commissioner of Internal Affairs","startDate":"2022-09-01",
                         "displayName":"Roos Kruk","nickname":"Skye"}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.nickname").value("Skye"))

            mvc.perform(get("/boards/{id}", board.id))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.members[0].nickname").value("Skye"))
        }

        @Test
        fun `a linked member is named by the account, not by what was recorded`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val user = createUserWithRole(Role.MEMBER)
            val member = addBoardMemberWithoutAccount(board, displayName = "Somebody Else")

            mvc.perform(
                put("/boards/{boardId}/members/{id}/member", board.id, member.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\": ${user.id}}")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.userId").value(user.id))
                .andExpect(jsonPath("$.name").value(user.fullName))

            // Detaching leaves the member standing under the name they were recorded with.
            mvc.perform(
                put("/boards/{boardId}/members/{id}/member", board.id, member.id)
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
            val member = addBoardMemberWithoutAccount(board, displayName = "Nobody Here")

            mvc.perform(
                put("/boards/{id}", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"number":${board.number},"name":"${board.name}","candidate":"${board.candidate}",
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
                .andExpect(jsonPath("$.members[?(@.id == %d)].name".format(member.id)).value("Nobody Here"))
        }

        /**
         * The asset file name and the uploaded photograph answer side by side.
         *
         * This is the expand half of an expand-contract: `/board` still draws `image` and #935
         * drops it, so a board that has both must answer with both.
         */
        @Test
        fun `a board answers with the photograph somebody chose, its size and its widths`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val photo = storedPicture(FileType.BOARD_PHOTO)

            mvc.perform(
                put("/boards/{id}", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"number":${board.number},"candidate":"${board.candidate}",
                         "startDate":"${board.startDate}","image":"board1/board1.jpg",
                         "photo":"$photo","version":${board.version}}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.photo.path").value(photo))
                .andExpect(jsonPath("$.photo.url").value("/files/public/$photo"))
                .andExpect(jsonPath("$.photo.width").value(800))
                .andExpect(jsonPath("$.photo.height").value(600))
                .andExpect(jsonPath("$.photo.renditions[0].width").value(320))
                .andExpect(jsonPath("$.photo.renditions[1].width").value(640))
                // The asset file name still answers beside it, because the page still reads it.
                .andExpect(jsonPath("$.image").value("board1/board1.jpg"))

            // Anybody may read a board, which is what the public page does.
            mvc.perform(get("/boards/{id}", board.id))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.photo.path").value(photo))
        }

        @Test
        fun `a board created with a photograph carries it from the start`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val photo = storedPicture(FileType.BOARD_PHOTO)

            mvc.perform(
                post("/boards")
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"number":${freeNumber()},"name":"Rainbow road","candidate":"Rainbow road",
                         "startDate":"${LocalDate.now()}","photo":"$photo"}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.photo.path").value(photo))
        }

        @Test
        fun `a photograph that names nothing stored is refused`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()

            mvc.perform(
                put("/boards/{id}", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"number":${board.number},"candidate":"${board.candidate}",
                         "startDate":"${board.startDate}","photo":"board-photos/nothing.webp",
                         "version":${board.version}}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("PictureNotStored"))
        }

        /**
         * A portrait is not a photograph, even though both are stored pictures.
         *
         * The kind is part of what a path resolves to, so a board's photograph field will not
         * take a portrait somebody uploaded for a member, which is what keeps a directory
         * meaning what it says.
         */
        @Test
        fun `a photograph field will not take a portrait`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val portrait = storedPicture(FileType.BOARD_PORTRAIT)

            mvc.perform(
                put("/boards/{id}", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"number":${board.number},"candidate":"${board.candidate}",
                         "startDate":"${board.startDate}","photo":"$portrait",
                         "version":${board.version}}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("PictureNotStored"))
        }

        @Test
        fun `a member answers with the portrait somebody chose, and keeps it through a correction`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()
            val portrait = storedPicture(FileType.BOARD_PORTRAIT, width = 400, height = 600)

            val created = mvc.perform(
                post("/boards/{boardId}/members", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"role":"Chair","startDate":"${board.startDate}",
                         "displayName":"Amber Scholtz","image":"board6/amber.jpg",
                         "portrait":"$portrait"}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.portrait.path").value(portrait))
                .andExpect(jsonPath("$.portrait.width").value(400))
                .andExpect(jsonPath("$.portrait.renditions[0].width").value(160))
                .andExpect(jsonPath("$.image").value("board6/amber.jpg"))
                .andReturn()
            val memberId = JsonPath.read<Int>(created.response.contentAsString, "$.id")

            mvc.perform(
                put("/boards/{boardId}/members/{id}", board.id, memberId)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"role":"Chair","startDate":"${board.startDate}",
                         "displayName":"Amber Scholtz","portrait":"$portrait"}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.portrait.path").value(portrait))

            mvc.perform(get("/boards/{id}", board.id))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.members[0].portrait.path").value(portrait))
        }

        @Test
        fun `a portrait that names nothing stored is refused`() {
            val boardUser = createUserWithRole(Role.BOARD)
            val board = createBoardFixture()

            mvc.perform(
                post("/boards/{boardId}/members", board.id)
                    .with(bearer(boardUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"role":"Chair","startDate":"${board.startDate}",
                         "displayName":"Amber Scholtz","portrait":"board-portraits/nothing.webp"}
                        """.trimIndent(),
                    )
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("PictureNotStored"))
        }
    }
}
