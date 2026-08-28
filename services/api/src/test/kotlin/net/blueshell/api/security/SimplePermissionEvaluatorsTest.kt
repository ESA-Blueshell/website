package net.blueshell.api.security

import net.blueshell.api.blog.domain.BlogPermission
import net.blueshell.api.board.domain.BoardPermission
import net.blueshell.api.domain.contribution.application.permission.ContributionPeriodPermission
import net.blueshell.api.domain.contribution.application.permission.ContributionReminderPermission
import net.blueshell.api.domain.event.application.permission.GuestPermission
import net.blueshell.api.jobs.domain.JobExecutionPermission
import net.blueshell.api.sponsor.domain.SponsorPermission
import net.blueshell.api.telemetry.domain.TelemetryPermission

import net.blueshell.api.blog.domain.BlogService
import net.blueshell.api.blog.persistence.Blog
import net.blueshell.api.board.domain.BoardService
import net.blueshell.api.board.persistence.Board
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.event.application.GuestService
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.sponsor.domain.SponsorService
import net.blueshell.api.sponsor.persistence.Sponsor
import net.blueshell.api.telemetry.domain.TelemetryService
import net.blueshell.api.telemetry.persistence.Telemetry
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.shared.enums.PlatformType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SimplePermissionEvaluatorsTest {

    @Nested
    inner class BlogPermissionEvaluator {
        private val service = mock<BlogService>()
        private val evaluator = BlogPermission(service)

        @Test
        fun `denies when authentication or permission is missing`() {
            assertThat(evaluator.hasPermission(null, null, "read")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), null, null)).isFalse()
        }

        @Test
        fun `allows reads for any authenticated user and gates writes to board`() {
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), null, "write")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), null, "delete")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), null, "delete")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), null, "unknown")).isFalse()
        }

        @Test
        fun `hasPermissionId delegates via id and null id path`() {
            val blog = mock<Blog>()
            whenever(service.findById(7L)).thenReturn(blog)

            assertThat(evaluator.hasPermissionId(boardAuth(), 7L, "delete")).isTrue()
            assertThat(evaluator.hasPermissionId(guestAuth(), null, "write")).isFalse()

            verify(service).findById(7L)
        }
    }

    @Nested
    inner class BoardPermissionEvaluator {
        private val service = mock<BoardService>()
        private val evaluator = BoardPermission(service)

        @Test
        fun `allows reads for authenticated users and gates board operations`() {
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "delete")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "members")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), null, "write")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), null, "members")).isFalse()
        }

        @Test
        fun `hasPermissionId resolves by id when provided`() {
            val board = mock<Board>()
            whenever(service.findById(11L)).thenReturn(board)

            assertThat(evaluator.hasPermissionId(boardAuth(), 11L, "members")).isTrue()
            verify(service).findById(11L)
        }
    }

    @Nested
    inner class ContributionPeriodPermissionEvaluator {
        private val service = mock<ContributionPeriodService>()
        private val evaluator = ContributionPeriodPermission(service)

        @Test
        fun `read is public to authenticated users and write delete require board`() {
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), null, "write")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "delete")).isTrue()
        }

        @Test
        fun `hasPermissionId loads target when id is present`() {
            val period = mock<ContributionPeriod>()
            whenever(service.findById(5L)).thenReturn(period)

            assertThat(evaluator.hasPermissionId(boardAuth(), 5L, "delete")).isTrue()
            verify(service).findById(5L)
        }
    }

    @Nested
    inner class ContributionReminderPermissionEvaluator {
        private val service = mock<ContributionReminderService>()
        private val evaluator = ContributionReminderPermission(service)

        @Test
        fun `only board can read write or delete`() {
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "delete")).isTrue()
        }

        @Test
        fun `hasPermissionId resolves reminder by id`() {
            val id = ContributionReminder.Id(userId = 1L, contributionPeriodId = 2L)
            val reminder = mock<ContributionReminder>()
            whenever(service.findById(id)).thenReturn(reminder)

            assertThat(evaluator.hasPermissionId(boardAuth(), id, "write")).isTrue()
            verify(service).findById(id)
        }
    }

    @Nested
    inner class JobExecutionPermissionEvaluator {
        private val service = mock<JobExecutionService>()
        private val evaluator = JobExecutionPermission(service)

        @Test
        fun `only admin can perform job actions`() {
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), null, "retry")).isFalse()
            assertThat(evaluator.hasPermission(adminAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermission(adminAuth(), null, "retry")).isTrue()
            assertThat(evaluator.hasPermission(adminAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermission(adminAuth(), null, "delete")).isTrue()
            assertThat(evaluator.hasPermission(adminAuth(), null, "unknown")).isFalse()
        }

        @Test
        fun `hasPermissionId checks null-id path and loaded-entity path`() {
            val execution = mock<JobExecution>()
            whenever(service.findById(4L)).thenReturn(execution)

            assertThat(evaluator.hasPermissionId(adminAuth(), null, "delete")).isTrue()
            assertThat(evaluator.hasPermissionId(adminAuth(), 4L, "delete")).isTrue()
            verify(service).findById(4L)
        }
    }

    @Nested
    inner class SponsorPermissionEvaluator {
        private val service = mock<SponsorService>()
        private val evaluator = SponsorPermission(service)

        @Test
        fun `read write and delete require board`() {
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "delete")).isTrue()
        }

        @Test
        fun `hasPermissionId resolves sponsor by id`() {
            val sponsor = mock<Sponsor>()
            whenever(service.findById(8L)).thenReturn(sponsor)

            assertThat(evaluator.hasPermissionId(boardAuth(), 8L, "read")).isTrue()
            verify(service).findById(8L)
        }
    }

    @Nested
    inner class TelemetryPermissionEvaluator {
        private val service = mock<TelemetryService>()
        private val evaluator = TelemetryPermission(service)
        private val entity = Telemetry(platform = PlatformType.TWITTER, url = "https://example.com")

        @Test
        fun `null entity allows write delete for board and rejects read`() {
            assertThat(evaluator.hasPermission(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "delete")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), null, "write")).isFalse()
        }

        @Test
        fun `existing entity permissions are board-only for read write delete`() {
            assertThat(evaluator.hasPermission(boardAuth(), entity, "read")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), entity, "write")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), entity, "delete")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), entity, "read")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), entity, "unknown")).isFalse()
        }

        @Test
        fun `hasPermissionId uses null-id fallback and loaded entity path`() {
            whenever(service.findById(3L)).thenReturn(entity)

            assertThat(evaluator.hasPermissionId(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermissionId(boardAuth(), 3L, "read")).isTrue()
            verify(service).findById(3L)
        }
    }

    @Nested
    inner class GuestPermissionEvaluator {
        private val service = mock<GuestService>()
        private val evaluator = GuestPermission(service)

        @Test
        fun `guest permissions require auth entity and permission and only allow read write`() {
            val guest = mock<Guest>()
            assertThat(evaluator.hasPermission(null, guest, "read")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), guest, null)).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), guest, "read")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), guest, "write")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), guest, "delete")).isFalse()
        }

        @Test
        fun `hasPermissionId resolves guest via access token`() {
            val guest = mock<Guest>()
            whenever(service.findByAccessToken("token-123")).thenReturn(guest)

            assertThat(evaluator.hasPermissionId(guestAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermissionId(guestAuth(), "token-123", "read")).isTrue()
            verify(service).findByAccessToken("token-123")
        }
    }
}
