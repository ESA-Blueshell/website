package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortMember
import net.blueshell.api.shared.enums.CohortMemberState
import net.blueshell.api.cohort.persistence.CohortSubject
import net.blueshell.api.cohort.persistence.CohortSubjectType
import net.blueshell.api.cohort.persistence.CohortMemberRepository
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.cohort.persistence.state
import net.blueshell.api.platform.integration.mock.MockTargetStrategy
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.persistence.ExternalIdMappingRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class CohortLedgerAutoflushIT : UserTestSupport() {

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var subjects: CohortSubjectRepository

    @Autowired
    private lateinit var members: CohortMemberRepository

    @Autowired
    private lateinit var externalIds: ExternalIdMappingRepository

    @Autowired
    private lateinit var remediation: CohortRemediationService

    @Autowired
    private lateinit var mockTarget: MockTargetStrategy

    @BeforeEach
    fun resetTarget() {
        mockTarget.clear()
    }

    @Test
    fun `confirming desired row with matching stranger does not violate external unique key`() {
        val user = createUserWithRole(Role.MEMBER)
        val subject = newSubject()
        val cohort = newCohort(subject, externalId = "list-99")
        members.saveAndFlush(CohortMember(cohort = cohort, userId = user.id!!, subject = subject))
        members.saveAndFlush(
            CohortMember(
                cohort = cohort,
                userId = null,
                subject = subject,
                externalUserId = "ext-1",
                verifiedAt = LocalDateTime.parse("2026-01-01T12:00:00"),
                label = "old stranger",
            ),
        )
        externalIds.saveAndFlush(ExternalIdMapping("USER", user.id!!, TargetSystem.BREVO.name, "ext-1"))
        mockTarget.seedMember("ext-1", "list-99", "Ada Remote")

        assertThatCode { remediation.verifyCohort(cohort.id!!) }.doesNotThrowAnyException()

        val desired = members.findByCohortIdAndUserId(cohort.id!!, user.id!!)!!
        assertThat(desired.externalUserId).isEqualTo("ext-1")
        assertThat(desired.label).isEqualTo("Ada Remote")
        assertThat(desired.state).isEqualTo(CohortMemberState.VERIFIED)
        assertThat(members.findByCohortIdAndExternalUserIdAndUserIdIsNull(cohort.id!!, "ext-1")).isNull()
    }

    @Test
    fun `rapid same-key delete re-add delete uses distinct soft-delete timestamps`() {
        val subject = newSubject()
        val cohort = newCohort(subject, externalId = "list-fast")
        val first = members.saveAndFlush(
            CohortMember(
                cohort = cohort,
                userId = null,
                subject = subject,
                externalUserId = "ext-fast",
                verifiedAt = LocalDateTime.parse("2026-01-01T12:00:00"),
            ),
        )

        assertThatCode {
            members.delete(first)
            members.flush()
            val second = members.saveAndFlush(
                CohortMember(
                    cohort = cohort,
                    userId = null,
                    subject = subject,
                    externalUserId = "ext-fast",
                    verifiedAt = LocalDateTime.parse("2026-01-01T12:00:01"),
                ),
            )
            members.delete(second)
            members.flush()
        }.doesNotThrowAnyException()

        val deletedAtValues = entityManager.createNativeQuery(
            """
            SELECT DATE_FORMAT(deleted_at, '%Y-%m-%d %H:%i:%s.%f')
            FROM cohort_member
            WHERE cohort_id = :cohortId
              AND external_user_id = :externalUserId
              AND deleted_at <> '9999-12-31 23:59:59'
            ORDER BY id
            """.trimIndent(),
        )
            .setParameter("cohortId", cohort.id!!)
            .setParameter("externalUserId", "ext-fast")
            .resultList
            .map { it.toString() }

        assertThat(deletedAtValues)
            .hasSize(2)
            .allMatch { it.matches(Regex(""".*\.\d{6}$""")) }
        assertThat(deletedAtValues.toSet()).hasSize(2)
    }

    private fun newSubject(): CohortSubject =
        subjects.saveAndFlush(CohortSubject(type = CohortSubjectType.NEWSLETTER_SUBSCRIBERS, label = "Members"))

    private fun newCohort(subject: CohortSubject, externalId: String): Cohort =
        cohorts.saveAndFlush(
            Cohort(
                system = TargetSystem.BREVO.name,
                kind = CohortKind.LIST,
                label = "Members",
                subjectId = subject.id,
                externalId = externalId,
            ),
        )
}
