package net.blueshell.api.domain.file.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for FileController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - BOARD and event organizers can download event banners
 * - COMMITTEE role can upload banners
 * - Unauthorized users cannot upload
 */
@SpringBootTest
class FileControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class DownloadEventBanner {

        @Test
        fun `allows BOARD to download event banners`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = 1L

            mvc.perform(
                get("/events/{eventId}/banners", eventId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows event organizer to download banner`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val eventId = 1L

            mvc.perform(
                get("/events/{eventId}/banners", eventId)
                    .with(bearer(committee))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies regular user from downloading unapproved event banners`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = 1L

            mvc.perform(
                get("/events/{eventId}/banners", eventId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val eventId = 1L

            mvc.perform(get("/events/{eventId}/banners", eventId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UploadEventBanner {

        @Test
        fun `allows COMMITTEE to upload banners`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                multipart("/events/banners")
                    .with(bearer(committee))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies BOARD from uploading banners`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                multipart("/events/banners")
                    .with(bearer(board))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies regular user from uploading banners`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                multipart("/events/banners")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from uploading banners`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                multipart("/events/banners")
                    .with(bearer(guest))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                multipart("/events/banners")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform COMMITTEE operations`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                multipart("/events/banners")
                    .with(bearer(admin))
            )
                .andExpect(status().isCreated)
        }
    }
}
