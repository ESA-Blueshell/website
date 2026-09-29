package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.cohort.persistence.CohortSubjectType
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.Duration

/** Registering a cohort queues the job that creates and links its list (api ADR-035). */
@SpringBootTest
@TestPropertySource(properties = ["app.jobs.auto-dispatch=true"])
class CohortTargetCreationIT : UserTestSupport() {
    @Autowired
    private lateinit var committees: CommitteeService

    @Autowired
    private lateinit var subjects: CohortSubjectRepository

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Test
    fun `a new committee's list is created and linked by its create-target job`() {
        val committee = committees.createWithMembers("Lijstcie", "Gets its list", emptyList())
        val subject = subjects.findByDefinitionKey("${CohortSubjectType.COMMITTEE_MEMBERS}:${committee.id}")!!

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted {
            val target = cohorts.findAllBySubjectId(subject.id!!).single()
            assertThat(target.externalId).isNotBlank()
            assertThat(target.folder).isEqualTo("Committees")
        }
    }
}
