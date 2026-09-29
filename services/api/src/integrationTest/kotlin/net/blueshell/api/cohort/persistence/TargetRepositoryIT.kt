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
 * Round-trip checks that the schema and the Target / TargetMember /
 * Cohort entities agree on column names and types. With
 * `hibernate.ddl-auto=none` Hibernate cannot fail at startup on a
 * mismatch, so this IT is the earliest place a typo here will surface.
 */
@SpringBootTest
class TargetRepositoryIT : UserTestSupport() {
    @Autowired
    private lateinit var targets: TargetRepository

    @Autowired
    private lateinit var targetMembers: TargetMemberRepository

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Test
    fun `cohort persists and reloads with all configured fields`() {
        val target =
            Target(
                system = TargetSystem.BREVO.name,
                kind = TargetKind.LIST,
                label = "Members",
            )

        val saved = targets.save(target)
        val reloaded = targets.findById(saved.id!!).orElseThrow()

        assertThat(reloaded.system).isEqualTo(TargetSystem.BREVO.name)
        assertThat(reloaded.kind).isEqualTo(TargetKind.LIST)
        assertThat(reloaded.label).isEqualTo("Members")
        assertThat(reloaded.isSoftDeleted).isFalse()
    }

    @Test
    fun `cohort filters by system and kind`() {
        targets.save(Target(TargetSystem.BREVO.name, TargetKind.LIST, "brevo-list"))
        targets.save(Target(TargetSystem.GOOGLE_CALENDAR.name, TargetKind.GROUP, "g-group"))

        assertThat(targets.findAllBySystem(TargetSystem.BREVO.name))
            .extracting<String> { it.label }
            .contains("brevo-list")
        assertThat(targets.findAllBySystemAndKind(TargetSystem.BREVO.name, TargetKind.ROLE))
            .isEmpty()
    }

    @Test
    fun `cohort member round-trips with FK to cohort and user_id`() {
        val user = createUserWithRole(Role.MEMBER)
        val cohort =
            cohorts.save(
                net.blueshell.api.cohort.persistence.Cohort(
                    type = net.blueshell.api.cohort.persistence.CohortType.NEWSLETTER_SUBSCRIBERS,
                    label = "Members",
                ),
            )
        val target =
            targets.save(
                Target(
                    system = TargetSystem.BREVO.name,
                    kind = TargetKind.LIST,
                    label = "Members",
                    cohortId = cohort.id,
                ),
            )

        val saved =
            targetMembers.save(
                TargetMember(target = target, userId = user.id!!, cohort = cohort),
            )
        val reloaded = targetMembers.findById(saved.id!!).orElseThrow()

        assertThat(reloaded.target.id).isEqualTo(target.id)
        assertThat(reloaded.userId).isEqualTo(user.id)

        assertThat(targetMembers.findAllByUserIdAndUserIdIsNotNull(user.id!!)).hasSize(1)
        assertThat(targetMembers.findAllByTargetId(target.id!!)).hasSize(1)
        assertThat(targetMembers.findByTargetIdAndUserId(target.id!!, user.id!!)?.id)
            .isEqualTo(saved.id)
    }

    @Test
    fun `a cohort is found by the definition that produces it`() {
        val cohort =
            cohorts.save(
                Cohort(
                    type = CohortType.NEWSLETTER_SUBSCRIBERS,
                    label = "Newsletter Subscribers",
                    definitionKey = "NEWSLETTER_SUBSCRIBERS",
                ),
            )
        val target =
            targets.save(
                Target(TargetSystem.BREVO.name, TargetKind.LIST, "Newsletter", cohortId = cohort.id),
            )

        assertThat(cohorts.findByDefinitionKey("NEWSLETTER_SUBSCRIBERS")?.id).isEqualTo(cohort.id)
        assertThat(targets.findAllByCohortId(cohort.id!!).map { it.id }).containsExactly(target.id)
        assertThat(cohorts.findByDefinitionKey("PERIOD_MEMBERS:404")).isNull()
    }

    // Ledger invariants introduced by V74.
    // The unified-ledger design rests on MariaDB allowing multiple NULLs in a
    // unique index. These ITs pin that behaviour down against a real database,
    // since `hibernate.ddl-auto=none` means a wrong assumption only surfaces here.

    @Test
    fun `stranger row persists with null user_id and an observed external id`() {
        val cohort = newCohort()
        val target = newTarget(cohort)

        val stranger =
            targetMembers.saveAndFlush(
                TargetMember(
                    target = target,
                    userId = null,
                    cohort = cohort,
                    externalUserId = "ext-stranger",
                    verifiedAt = LocalDateTime.now(),
                ),
            )

        assertThat(targetMembers.findById(stranger.id!!).orElseThrow().userId).isNull()
        assertThat(targetMembers.findByTargetIdAndExternalUserIdAndUserIdIsNull(target.id!!, "ext-stranger")?.id)
            .isEqualTo(stranger.id)
        assertThat(targetMembers.findAllByTargetIdAndUserIdIsNull(target.id!!)).hasSize(1)
    }

    @Test
    fun `multiple desired rows with null external_user_id coexist in one cohort`() {
        val cohort = newCohort()
        val target = newTarget(cohort)
        val a = createUserWithRole(Role.MEMBER)
        val b = createUserWithRole(Role.MEMBER)

        targetMembers.saveAndFlush(TargetMember(target = target, userId = a.id!!, cohort = cohort))
        targetMembers.saveAndFlush(TargetMember(target = target, userId = b.id!!, cohort = cohort))

        // uk_cohort_member_external is (cohort_id, external_user_id, deleted_at);
        // both rows share (cohort, NULL, sentinel) and must not collide.
        assertThat(targetMembers.findAllByTargetIdAndUserIdIsNotNull(target.id!!)).hasSize(2)
    }

    @Test
    fun `multiple stranger rows with distinct external ids coexist in one cohort`() {
        val cohort = newCohort()
        val target = newTarget(cohort)
        val now = LocalDateTime.now()

        targetMembers.saveAndFlush(
            TargetMember(target = target, userId = null, cohort = cohort, externalUserId = "ext-a", verifiedAt = now),
        )
        targetMembers.saveAndFlush(
            TargetMember(target = target, userId = null, cohort = cohort, externalUserId = "ext-b", verifiedAt = now),
        )

        assertThat(targetMembers.findAllByTargetIdAndUserIdIsNull(target.id!!)).hasSize(2)
    }

    @Test
    fun `a duplicate active stranger for the same external id is rejected`() {
        val cohort = newCohort()
        val target = newTarget(cohort)
        val now = LocalDateTime.now()
        targetMembers.saveAndFlush(
            TargetMember(target = target, userId = null, cohort = cohort, externalUserId = "ext-dup", verifiedAt = now),
        )

        assertThatThrownBy {
            targetMembers.saveAndFlush(
                TargetMember(
                    target = target,
                    userId = null,
                    cohort = cohort,
                    externalUserId = "ext-dup",
                    verifiedAt = now,
                ),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun newCohort(): Cohort = cohorts.save(Cohort(type = CohortType.NEWSLETTER_SUBSCRIBERS, label = "Members"))

    private fun newTarget(cohort: Cohort): Target =
        targets.save(
            Target(
                system = TargetSystem.BREVO.name,
                kind = TargetKind.LIST,
                label = "Members",
                cohortId = cohort.id,
            ),
        )
}
