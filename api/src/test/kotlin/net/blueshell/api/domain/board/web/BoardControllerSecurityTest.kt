package net.blueshell.api.domain.board.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for BoardController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - BOARD users can create/update/delete boards and manage members
 * - Non-BOARD users cannot modify boards
 * - Public read access to board information
 */
@SpringBootTest
class BoardControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class CreateBoard {

        @Test
        fun `allows BOARD to create boards`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/boards")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"New Board","year":2026}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from creating boards`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/boards")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"New Board","year":2026}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from creating boards`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                post("/boards")
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"New Board","year":2026}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                post("/boards")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"New Board","year":2026}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindAllBoards {

        @Test
        fun `allows anyone to list all boards`() {
            mvc.perform(get("/boards"))
                .andExpect(status().isOk)
        }

        @Test
        fun `allows authenticated user to list boards`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/boards")
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to list boards`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/boards")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows unauthenticated access`() {
            mvc.perform(get("/boards"))
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class FindBoardById {

        @Test
        fun `allows anyone to read board details`() {
            val boardId = 1L

            mvc.perform(get("/boards/{id}", boardId))
                .andExpect(status().isOk)
        }

        @Test
        fun `allows authenticated user to read board details`() {
            val member = createUserWithRole(Role.MEMBER)
            val boardId = 1L

            mvc.perform(
                get("/boards/{id}", boardId)
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows unauthenticated access to board details`() {
            val boardId = 1L

            mvc.perform(get("/boards/{id}", boardId))
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class UpdateBoard {

        @Test
        fun `allows BOARD to update boards`() {
            val board = createUserWithRole(Role.BOARD)
            val boardId = 1L

            mvc.perform(
                put("/boards/{id}", boardId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Updated Board","year":2026}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from updating boards`() {
            val member = createUserWithRole(Role.MEMBER)
            val boardId = 1L

            mvc.perform(
                put("/boards/{id}", boardId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Hacked Board","year":2026}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from updating boards`() {
            val guest = createUserWithRole(Role.GUEST)
            val boardId = 1L

            mvc.perform(
                put("/boards/{id}", boardId)
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Hacked Board","year":2026}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val boardId = 1L

            mvc.perform(
                put("/boards/{id}", boardId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Unauthorized Update","year":2026}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class DeleteBoard {

        @Test
        fun `allows BOARD to delete boards`() {
            val board = createUserWithRole(Role.BOARD)
            val boardId = 1L

            mvc.perform(
                delete("/boards/{id}", boardId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies non-BOARD users from deleting boards`() {
            val member = createUserWithRole(Role.MEMBER)
            val boardId = 1L

            mvc.perform(
                delete("/boards/{id}", boardId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from deleting boards`() {
            val guest = createUserWithRole(Role.GUEST)
            val boardId = 1L

            mvc.perform(
                delete("/boards/{id}", boardId)
                    .with(bearer(guest))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val boardId = 1L

            mvc.perform(delete("/boards/{id}", boardId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class AddBoardMember {

        @Test
        fun `allows BOARD to add members`() {
            val board = createUserWithRole(Role.BOARD)
            val boardId = 1L
            val userId = 2L

            mvc.perform(
                post("/boards/{boardId}/members", boardId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":$userId,"role":"CHAIR"}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from adding members`() {
            val member = createUserWithRole(Role.MEMBER)
            val boardId = 1L
            val userId = 2L

            mvc.perform(
                post("/boards/{boardId}/members", boardId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":$userId,"role":"CHAIR"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val boardId = 1L
            val userId = 2L

            mvc.perform(
                post("/boards/{boardId}/members", boardId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":$userId,"role":"CHAIR"}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RemoveBoardMember {

        @Test
        fun `allows BOARD to remove members`() {
            val board = createUserWithRole(Role.BOARD)
            val boardId = 1L
            val userId = 2L

            mvc.perform(
                delete("/boards/{boardId}/members/{userId}", boardId, userId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies non-BOARD users from removing members`() {
            val member = createUserWithRole(Role.MEMBER)
            val boardId = 1L
            val userId = 2L

            mvc.perform(
                delete("/boards/{boardId}/members/{userId}", boardId, userId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val boardId = 1L
            val userId = 2L

            mvc.perform(
                delete("/boards/{boardId}/members/{userId}", boardId, userId)
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                post("/boards")
                    .with(bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"New Board","year":2026}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `COMMITTEE cannot create boards (not BOARD)`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                post("/boards")
                    .with(bearer(committee))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"New Board","year":2026}""")
            )
                .andExpect(status().isForbidden)
        }
    }
}
