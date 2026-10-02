package net.blueshell.api.cohort.persistence

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetMemberState
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * The previous release still reads and writes `cohort` and `cohort_member` under blue/green, so
 * the views left behind by the rename must take its inserts, updates and deletes, and land them in
 * `target` and `target_member`.
 */
@SpringBootTest
class TargetTableViewsIT : UserTestSupport() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var targets: TargetRepository

    @Autowired
    private lateinit var members: TargetMemberRepository

    @Test
    fun `the old views take the previous release's writes, and the new tables hold them`() {
        val cohort = cohorts.saveAndFlush(Cohort(type = CohortType.COMMITTEE_MEMBERS, label = "Views ${UUID.randomUUID()}"))
        val user = createUserWithRole(Role.MEMBER)
        val label = "Old release ${UUID.randomUUID()}"

        jdbc.update("INSERT INTO cohort (system, kind, label, subject_id) VALUES ('BREVO', 'LIST', ?, ?)", label, cohort.id)
        val targetId = jdbc.queryForObject("SELECT id FROM cohort WHERE label = ?", Long::class.java, label)!!
        jdbc.update("INSERT INTO cohort_member (cohort_id, subject_id, user_id) VALUES (?, ?, ?)", targetId, cohort.id, user.id)
        jdbc.update("UPDATE cohort SET folder = 'Committees' WHERE id = ?", targetId)

        val target = targets.findById(targetId).orElseThrow()
        assertThat(target.cohortId).isEqualTo(cohort.id)
        assertThat(target.folder).isEqualTo("Committees")
        val member = members.findByTargetIdAndUserId(targetId, user.id!!)!!
        assertThat(member.state).isEqualTo(TargetMemberState.DESIRED)
        assertThat(
            jdbc.queryForObject("SELECT cohort_id FROM target_member WHERE id = ?", Long::class.java, member.id),
        ).isEqualTo(cohort.id)

        jdbc.update("DELETE FROM cohort_member WHERE id = ?", member.id)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM target_member WHERE id = ?", Int::class.java, member.id)).isZero()
        jdbc.update("DELETE FROM cohort WHERE id = ?", targetId)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM target WHERE id = ?", Int::class.java, targetId)).isZero()
    }
}
