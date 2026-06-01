package net.blueshell.api.platform.integration.cohort.persistence

import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRuleRepository
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Round-trip checks that the V66 schema and the Cohort / CohortMember /
 * CohortRule entities agree on column names and types. With
 * `hibernate.ddl-auto=none` Hibernate cannot fail at startup on a
 * mismatch, so this IT is the earliest place a typo here will surface.
 */
@SpringBootTest
class CohortRepositoryIT : UserTestSupport() {

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var cohortMembers: CohortMemberRepository

    @Autowired
    private lateinit var cohortRules: CohortRuleRepository

    @Test
    fun `cohort persists and reloads with all configured fields`() {
        val cohort = Cohort(
            system = TargetSystem.BREVO.name,
            kind = CohortKind.LIST,
            label = "Members",
        )

        val saved = cohorts.save(cohort)
        val reloaded = cohorts.findById(saved.id!!).orElseThrow()

        assertThat(reloaded.system).isEqualTo(TargetSystem.BREVO.name)
        assertThat(reloaded.kind).isEqualTo(CohortKind.LIST)
        assertThat(reloaded.label).isEqualTo("Members")
        assertThat(reloaded.isSoftDeleted).isFalse()
    }

    @Test
    fun `cohort filters by system and kind`() {
        cohorts.save(Cohort(TargetSystem.BREVO.name, CohortKind.LIST, "brevo-list"))
        cohorts.save(Cohort(TargetSystem.GOOGLE_CALENDAR.name, CohortKind.GROUP, "g-group"))

        assertThat(cohorts.findAllBySystem(TargetSystem.BREVO.name))
            .extracting<String> { it.label }
            .contains("brevo-list")
        assertThat(cohorts.findAllBySystemAndKind(TargetSystem.BREVO.name, CohortKind.ROLE))
            .isEmpty()
    }

    @Test
    fun `cohort member round-trips with FK to cohort and user_id`() {
        val user = createUserWithRole(Role.MEMBER)
        val cohort = cohorts.save(Cohort(TargetSystem.BREVO.name, CohortKind.LIST, "Members"))

        val saved = cohortMembers.save(CohortMember(cohort = cohort, userId = user.id!!))
        val reloaded = cohortMembers.findById(saved.id!!).orElseThrow()

        assertThat(reloaded.cohort.id).isEqualTo(cohort.id)
        assertThat(reloaded.userId).isEqualTo(user.id)

        assertThat(cohortMembers.findAllByUserId(user.id!!)).hasSize(1)
        assertThat(cohortMembers.findAllByCohortId(cohort.id!!)).hasSize(1)
        assertThat(cohortMembers.findByCohortIdAndUserId(cohort.id!!, user.id!!)?.id)
            .isEqualTo(saved.id)
    }

    @Test
    fun `cohort rule lookup by fact returns enabled rows only`() {
        val cohort = cohorts.save(Cohort(TargetSystem.BREVO.name, CohortKind.LIST, "Members"))

        cohortRules.save(
            CohortRule(
                factKind = CohortFactKind.ROLE,
                factKey = Role.MEMBER.name,
                cohort = cohort,
                enabled = true,
            )
        )
        cohortRules.save(
            CohortRule(
                factKind = CohortFactKind.ROLE,
                factKey = Role.BOARD.name,
                cohort = cohort,
                enabled = false,
            )
        )

        val memberRules =
            cohortRules.findAllByFactKindAndFactKeyAndEnabledTrue(CohortFactKind.ROLE, Role.MEMBER.name)
        val boardRules =
            cohortRules.findAllByFactKindAndFactKeyAndEnabledTrue(CohortFactKind.ROLE, Role.BOARD.name)

        assertThat(memberRules).hasSize(1)
        assertThat(boardRules).isEmpty()
        assertThat(cohortRules.findAllByCohortId(cohort.id!!)).hasSize(2)
    }
}
