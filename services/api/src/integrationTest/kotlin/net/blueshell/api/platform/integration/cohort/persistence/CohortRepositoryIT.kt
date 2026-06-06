package net.blueshell.api.platform.integration.cohort.persistence

import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime

/**
 * Round-trip checks that the schema and the Cohort / CohortMember /
 * CohortSubject entities agree on column names and types. With
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
    private lateinit var subjects: CohortSubjectRepository

    @Test
    fun `cohort persists and reloads with all configured fields`() {
        val cohort = Cohort(
            system = TargetSystem.BREVO,
            kind = CohortKind.LIST,
            label = "Members",
        )

        val saved = cohorts.save(cohort)
        val reloaded = cohorts.findById(saved.id!!).orElseThrow()

        assertThat(reloaded.system).isEqualTo(TargetSystem.BREVO)
        assertThat(reloaded.kind).isEqualTo(CohortKind.LIST)
        assertThat(reloaded.label).isEqualTo("Members")
        assertThat(reloaded.isSoftDeleted).isFalse()
    }

    @Test
    fun `cohort filters by system and kind`() {
        cohorts.save(Cohort(TargetSystem.BREVO, CohortKind.LIST, "brevo-list"))
        cohorts.save(Cohort(TargetSystem.GOOGLE_CALENDAR, CohortKind.GROUP, "g-group"))

        assertThat(cohorts.findAllBySystem(TargetSystem.BREVO))
            .extracting<String> { it.label }
            .contains("brevo-list")
        assertThat(cohorts.findAllBySystemAndKind(TargetSystem.BREVO, CohortKind.ROLE))
            .isEmpty()
    }

    @Test
    fun `cohort member round-trips with FK to cohort and user_id`() {
        val user = createUserWithRole(Role.MEMBER)
        val subject = subjects.save(
            net.blueshell.api.platform.integration.cohort.persistence.CohortSubject(
                type = net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType.CUSTOM,
                label = "Members",
            )
        )
        val cohort = cohorts.save(
            Cohort(
                system = TargetSystem.BREVO,
                kind = CohortKind.LIST,
                label = "Members",
                subjectId = subject.id,
            )
        )

        val saved = cohortMembers.save(
            CohortMember(cohort = cohort, userId = user.id!!, subject = subject)
        )
        val reloaded = cohortMembers.findById(saved.id!!).orElseThrow()

        assertThat(reloaded.cohort.id).isEqualTo(cohort.id)
        assertThat(reloaded.userId).isEqualTo(user.id)

        assertThat(cohortMembers.findAllByUserIdAndUserIdIsNotNull(user.id!!)).hasSize(1)
        assertThat(cohortMembers.findAllByCohortId(cohort.id!!)).hasSize(1)
        assertThat(cohortMembers.findByCohortIdAndUserId(cohort.id!!, user.id!!)?.id)
            .isEqualTo(saved.id)
    }

    @Test
    fun `findAllForEnabledSubjectFact returns cohorts for enabled subjects only`() {
        // The rule now lives on the subject: one subject per (factKind, factKey),
        // carrying the enabled flag the evaluator filters on.
        val enabledSubject = subjects.save(
            CohortSubject(
                type = CohortSubjectType.CUSTOM,
                label = "Members",
                factKind = CohortFactKind.ROLE,
                factKey = Role.MEMBER.name,
                enabled = true,
            ),
        )
        val disabledSubject = subjects.save(
            CohortSubject(
                type = CohortSubjectType.CUSTOM,
                label = "Board",
                factKind = CohortFactKind.ROLE,
                factKey = Role.BOARD.name,
                enabled = false,
            ),
        )
        val memberCohort = cohorts.save(
            Cohort(TargetSystem.BREVO, CohortKind.LIST, "Members", subjectId = enabledSubject.id),
        )
        cohorts.save(Cohort(TargetSystem.BREVO, CohortKind.LIST, "Board", subjectId = disabledSubject.id))

        assertThat(cohorts.findAllForEnabledSubjectFact(CohortFactKind.ROLE, Role.MEMBER.name).map { it.id })
            .containsExactly(memberCohort.id)
        assertThat(cohorts.findAllForEnabledSubjectFact(CohortFactKind.ROLE, Role.BOARD.name)).isEmpty()
    }

    // ── Ledger invariants (V74) ───────────────────────────────────────────────
    // The unified-ledger design rests on MariaDB allowing multiple NULLs in a
    // unique index. These ITs pin that behaviour down against a real database,
    // since `hibernate.ddl-auto=none` means a wrong assumption only surfaces here.

    @Test
    fun `stranger row persists with null user_id and an observed external id`() {
        val subject = newSubject()
        val cohort = newCohort(subject)

        val stranger = cohortMembers.saveAndFlush(
            CohortMember(
                cohort = cohort,
                userId = null,
                subject = subject,
                externalUserId = "ext-stranger",
                verifiedAt = LocalDateTime.now(),
            )
        )

        assertThat(cohortMembers.findById(stranger.id!!).orElseThrow().userId).isNull()
        assertThat(cohortMembers.findByCohortIdAndExternalUserIdAndUserIdIsNull(cohort.id!!, "ext-stranger")?.id)
            .isEqualTo(stranger.id)
        assertThat(cohortMembers.findAllByCohortIdAndUserIdIsNull(cohort.id!!)).hasSize(1)
    }

    @Test
    fun `multiple desired rows with null external_user_id coexist in one cohort`() {
        val subject = newSubject()
        val cohort = newCohort(subject)
        val a = createUserWithRole(Role.MEMBER)
        val b = createUserWithRole(Role.MEMBER)

        cohortMembers.saveAndFlush(CohortMember(cohort = cohort, userId = a.id!!, subject = subject))
        cohortMembers.saveAndFlush(CohortMember(cohort = cohort, userId = b.id!!, subject = subject))

        // uk_cohort_member_external is (cohort_id, external_user_id, deleted_at);
        // both rows share (cohort, NULL, sentinel) and must not collide.
        assertThat(cohortMembers.findAllByCohortIdAndUserIdIsNotNull(cohort.id!!)).hasSize(2)
    }

    @Test
    fun `multiple stranger rows with distinct external ids coexist in one cohort`() {
        val subject = newSubject()
        val cohort = newCohort(subject)
        val now = LocalDateTime.now()

        cohortMembers.saveAndFlush(
            CohortMember(cohort = cohort, userId = null, subject = subject, externalUserId = "ext-a", verifiedAt = now)
        )
        cohortMembers.saveAndFlush(
            CohortMember(cohort = cohort, userId = null, subject = subject, externalUserId = "ext-b", verifiedAt = now)
        )

        assertThat(cohortMembers.findAllByCohortIdAndUserIdIsNull(cohort.id!!)).hasSize(2)
    }

    @Test
    fun `a duplicate active stranger for the same external id is rejected`() {
        val subject = newSubject()
        val cohort = newCohort(subject)
        val now = LocalDateTime.now()
        cohortMembers.saveAndFlush(
            CohortMember(cohort = cohort, userId = null, subject = subject, externalUserId = "ext-dup", verifiedAt = now)
        )

        assertThatThrownBy {
            cohortMembers.saveAndFlush(
                CohortMember(
                    cohort = cohort,
                    userId = null,
                    subject = subject,
                    externalUserId = "ext-dup",
                    verifiedAt = now,
                )
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun newSubject(): CohortSubject =
        subjects.save(CohortSubject(type = CohortSubjectType.CUSTOM, label = "Members"))

    private fun newCohort(subject: CohortSubject): Cohort =
        cohorts.save(
            Cohort(
                system = TargetSystem.BREVO,
                kind = CohortKind.LIST,
                label = "Members",
                subjectId = subject.id,
            )
        )
}
