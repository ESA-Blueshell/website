package net.blueshell.api.persistence

import net.blueshell.api.domain.board.persistence.BoardDocument
import net.blueshell.api.domain.board.persistence.BoardMember
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.domain.event.persistence.EventPicture
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * Verifies that @SQLDelete correctly soft-deletes all @EmbeddedId entities.
 *
 * Hibernate binds @EmbeddedId fields alphabetically when executing @SQLDelete,
 * so the WHERE clause parameter order must match. These tests guard against
 * silent soft-delete failures caused by parameter order mismatches.
 */
@SpringBootTest
class EmbeddedIdSoftDeleteIT : UserTestSupport() {

    @Test
    fun `Contribution soft-delete updates deleted_at`() {
        val user = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        val entity = persist(Contribution(user = user, contributionPeriod = period))

        removeAndFlush(entity)

        assertSoftDeleted("contributions", "user_id = ${user.id} AND contribution_period_id = ${period.id}")
    }

    @Test
    fun `ContributionReminder soft-delete updates deleted_at`() {
        val user = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        val entity = persist(ContributionReminder(user = user, contributionPeriod = period))

        removeAndFlush(entity)

        assertSoftDeleted("contribution_reminders", "user_id = ${user.id} AND contribution_period_id = ${period.id}")
    }

    @Test
    fun `EventBanner soft-delete updates deleted_at`() {
        val event = createEventFixture()
        val file = createFileFixture(type = FileType.EVENT_BANNER)
        val entity = persist(EventBanner(event = event, file = file))

        removeAndFlush(entity)

        assertSoftDeleted("event_banners", "event_id = ${event.id} AND file_id = ${file.id}")
    }

    @Test
    fun `EventPicture soft-delete updates deleted_at`() {
        val event = createEventFixture()
        val file = createFileFixture(type = FileType.EVENT_PICTURE)
        val entity = persist(EventPicture(event = event, picture = file))

        removeAndFlush(entity)

        assertSoftDeleted("event_pictures", "event_id = ${event.id} AND picture_id = ${file.id}")
    }

    @Test
    fun `CommitteeMember soft-delete updates deleted_at`() {
        val committee = createCommitteeFixture()
        val user = createUserWithRole(Role.MEMBER)
        val entity = persist(CommitteeMember(committee = committee, user = user, role = "Member"))

        removeAndFlush(entity)

        assertSoftDeleted("committee_members", "committee_id = ${committee.id} AND user_id = ${user.id}")
    }

    @Test
    fun `BoardMember soft-delete updates deleted_at`() {
        val board = createBoardFixture()
        val user = createUserWithRole(Role.MEMBER)
        val entity = persist(BoardMember(board = board, user = user, role = "CHAIR", startDate = java.time.LocalDate.now()))

        removeAndFlush(entity)

        assertSoftDeleted("board_members", "board_id = ${board.id} AND user_id = ${user.id}")
    }

    @Test
    fun `BoardDocument soft-delete updates deleted_at`() {
        val board = createBoardFixture()
        val file = createFileFixture(type = FileType.DOCUMENT)
        val entity = persist(BoardDocument(board = board, file = file, name = "doc.pdf"))

        removeAndFlush(entity)

        assertSoftDeleted("board_documents", "board_id = ${board.id} AND file_id = ${file.id}")
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun removeAndFlush(entity: Any) {
        transactionTemplate.execute {
            val managed = entityManager.merge(entity)
            entityManager.remove(managed)
            entityManager.flush()
        }
    }

    private fun assertSoftDeleted(table: String, whereClause: String) {
        val result = transactionTemplate.execute {
            entityManager.clear()
            entityManager.createNativeQuery(
                "SELECT deleted_at FROM $table WHERE $whereClause"
            ).singleResult
        }
        assertThat(result.toString())
            .describedAs("$table row should be soft-deleted (deleted_at != sentinel)")
            .isNotEqualTo("9999-12-31T23:59:59")
    }
}
