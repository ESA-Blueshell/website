package net.blueshell.api.jobs.web

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.jobs.persistence.JobExecution
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class JobExecutionViewServiceTest {
    private val users: UserService = mockk()

    private fun resolver(
        fields: List<String>,
        type: String,
        label: (Long) -> String,
        implied: (Long) -> List<JobSubject> = { emptyList() },
    ) = object : JobSubjectResolver {
        override val payloadFields = fields
        override val entityType = type
        override fun label(id: Long) = label(id)
        override fun implied(id: Long) = implied(id)
    }

    private val eventResolver = resolver(listOf("eventId"), "EVENT", { "Event #$it: Gala" })
    private val periodResolver =
        resolver(listOf("contributionPeriodId", "periodId"), "CONTRIBUTION_PERIOD", { "Period #$it" })
    private val signUpResolver = resolver(
        fields = listOf("eventSignUpId"),
        type = "EVENT_SIGNUP",
        label = { "Event sign-up #$it" },
        implied = { listOf(JobSubject("userId", 7L), JobSubject("eventId", 3L)) },
    )

    private fun service(vararg resolvers: JobSubjectResolver) =
        JobExecutionViewService(JsonMapper(), users, resolvers.toList())

    /** `createdAt` and `updatedAt` are auditing fields JPA would populate. */
    private fun execution(payload: String? = null) =
        JobExecution(jobType = "test", payload = payload).apply {
            createdAt = Instant.EPOCH
            updatedAt = Instant.EPOCH
        }

    private fun stubUser(id: Long, name: String, username: String) {
        val user: User = mockk()
        every { user.fullName } returns name
        every { user.username } returns username
        every { users.findById(id) } returns user
    }

    @Test
    fun `related entities follow resolver order, with the payload user first`() {
        stubUser(7L, "Ada Lovelace", "ada")

        val dto = service(eventResolver, periodResolver).toDto(
            execution("""{"userId":7,"eventId":3,"contributionPeriodId":9}"""),
        )

        assertThat(dto.relatedEntities.map { it.type })
            .containsExactly("USER", "EVENT", "CONTRIBUTION_PERIOD")
        assertThat(dto.relatedEntities.map { it.label })
            .containsExactly("Ada Lovelace (@ada)", "Event #3: Gala", "Period #9")
    }

    @Test
    fun `a resolver reads whichever of its payload fields is present`() {
        val dto = service(periodResolver).toDto(execution("""{"periodId":4}"""))

        assertThat(dto.relatedEntities).singleElement()
            .satisfies({ assertThat(it.type).isEqualTo("CONTRIBUTION_PERIOD") })
        assertThat(dto.relatedEntities.single().id).isEqualTo(4L)
    }

    @Test
    fun `implied subjects are added after their parent and never duplicated`() {
        stubUser(7L, "Ada Lovelace", "ada")

        val dto = service(eventResolver, signUpResolver).toDto(
            execution("""{"userId":7,"eventId":3,"eventSignUpId":11}"""),
        )

        assertThat(dto.relatedEntities.map { it.type })
            .containsExactly("USER", "EVENT", "EVENT_SIGNUP")
    }

    @Test
    fun `an implied subject the payload did not name is still listed`() {
        stubUser(7L, "Ada Lovelace", "ada")

        val dto = service(eventResolver, signUpResolver).toDto(execution("""{"eventSignUpId":11}"""))

        assertThat(dto.relatedEntities.map { it.type })
            .containsExactly("EVENT_SIGNUP", "USER", "EVENT")
    }

    @Test
    fun `a payload field no resolver claims is ignored`() {
        val dto = service(eventResolver).toDto(execution("""{"invoiceId":99}"""))

        assertThat(dto.relatedEntities).isEmpty()
    }

    @Test
    fun `a payload that is absent or unparseable yields no related entities`() {
        assertThat(service(eventResolver).toDto(execution()).relatedEntities).isEmpty()
        assertThat(service(eventResolver).toDto(execution("not json")).relatedEntities).isEmpty()
    }
}
