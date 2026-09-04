package net.blueshell.api.file.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
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
    /** A real picture: a banner is converted on the way in, so bytes that only claim to be
     *  one are refused before any of these rules are reached. */
    private fun bannerFile() = MockMultipartFile(
        "file",
        "banner.png",
        "image/png",
        pngBytes(),
    )

    private fun pngBytes(width: Int = 800, height: Int = 450): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Nested
    inner class DownloadEventBanner {

        @Test
        fun `allows BOARD to download event banners`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture()
            attachEventBanner(event)
            val eventId = event.id!!

            mvc.perform(
                get("/events/{eventId}/banners", eventId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows event organizer to download banner`() {
            val committeeUser = createUserWithRole(Role.COMMITTEE)
            val committee = createCommitteeFixture()
            addCommitteeMember(committee, committeeUser)
            val event = createEventFixture(committee = committee)
            attachEventBanner(event)
            val eventId = event.id!!

            mvc.perform(
                get("/events/{eventId}/banners", eventId)
                    .with(bearer(committeeUser))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies regular user from downloading unapproved event banners`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = createEventFixture(approved = false).id!!

            mvc.perform(
                get("/events/{eventId}/banners", eventId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val event = createEventFixture(approved = false)
            attachEventBanner(event)
            val eventId = event.id!!

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
                    .file(bannerFile())
                    .with(bearer(committee))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `allows BOARD to upload banners`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                multipart("/events/banners")
                    .file(bannerFile())
                    .with(bearer(board))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies regular user from uploading banners`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                multipart("/events/banners")
                    .file(bannerFile())
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from uploading banners`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                multipart("/events/banners")
                    .file(bannerFile())
                    .with(bearer(guest))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                    multipart("/events/banners")
                        .file(bannerFile())
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
                    .file(bannerFile())
                    .with(bearer(admin))
            )
                .andExpect(status().isCreated)
        }
    }
}
