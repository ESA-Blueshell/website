package net.blueshell.api.cohort.persistence

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetSystem
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
        val subject = subjects.save(
            net.blueshell.api.cohort.persistence.CohortSubject(
                type = net.blueshell.api.cohort.persistence.CohortSubjectType.NEWSLETTER_SUBSCRIBERS,
                label = "Members",
            )
        )
        val cohort = cohorts.save(
            Cohort(
                system = TargetSystem.BREVO.name,
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
    fun `a subject is found by the definition that produces it`() {
        val subject = subjects.save(
            CohortSubject(
                type = CohortSubjectType.NEWSLETTER_SUBSCRIBERS,
                label = "Newsletter Subscribers",
                definitionKey = "NEWSLETTER_SUBSCRIBERS",
            ),
        )
        val cohort = cohorts.save(
            Cohort(TargetSystem.BREVO.name, CohortKind.LIST, "Newsletter", subjectId = subject.id),
        )

        assertThat(subjects.findByDefinitionKey("NEWSLETTER_SUBSCRIBERS")?.id).isEqualTo(subject.id)
        assertThat(cohorts.findAllBySubjectId(subject.id!!).map { it.id }).containsExactly(cohort.id)
        assertThat(subjects.findByDefinitionKey("PERIOD_MEMBERS:404")).isNull()
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
        subjects.save(CohortSubject(type = CohortSubjectType.NEWSLETTER_SUBSCRIBERS, label = "Members"))

    private fun newCohort(subject: CohortSubject): Cohort =
        cohorts.save(
            Cohort(
                system = TargetSystem.BREVO.name,
                kind = CohortKind.LIST,
                label = "Members",
                subjectId = subject.id,
            )
        )
}
