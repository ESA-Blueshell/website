package net.blueshell.api.alerts

import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@SpringBootTest
class AlertsIT : UserTestSupport() {
    private fun alertsOf(reader: User): List<Map<String, Any?>> {
        val body =
            mvc
                .perform(get("/management/alerts").with(signedIn(reader)))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        @Suppress("UNCHECKED_CAST")
        return mapper.readValue(body, List::class.java) as List<Map<String, Any?>>
    }

    private fun keysOf(reader: User) = alertsOf(reader).map { it["key"] as String }

    private fun post(
        reader: User,
        path: String,
        key: String,
    ) = mvc.perform(
        post("/management/alerts/$path")
            .with(signedIn(reader))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"key":"$key"}"""),
    )

    @Test
    fun `a dead job raises an alert for admins that clears once it is retried`() {
        val admin = createUserWithRole(Role.ADMIN)
        val dead = createJobExecutionFixture(status = JobExecutionStatus.DEAD)

        assertThat(keysOf(admin)).contains("job-dead:${dead.id}")

        transactionTemplate.executeWithoutResult {
            val job = jobExecutions.findById(dead.id!!).orElseThrow()
            job.status = JobExecutionStatus.QUEUED
        }

        assertThat(keysOf(admin)).doesNotContain("job-dead:${dead.id}")
    }

    @Test
    fun `a role waiting on two-factor raises an alert until its holder sets it up`() {
        val admin = createUserWithRole(Role.ADMIN)
        val waiting = createUserWithRole(Role.BOARD, twoFactor = false)

        assertThat(keysOf(admin)).contains("role-awaiting-two-factor:${waiting.id}")

        transactionTemplate.executeWithoutResult {
            userRepository.findById(waiting.id!!).orElseThrow().twoFactorSince = Instant.now()
        }

        assertThat(keysOf(admin)).doesNotContain("role-awaiting-two-factor:${waiting.id}")
    }

    @Test
    fun `admin-only alerts never show to the board or the treasurer`() {
        createJobExecutionFixture(status = JobExecutionStatus.DEAD)
        createUserWithRole(Role.BOARD, twoFactor = false)
        val adminOnly = setOf("JOB_DEAD", "EXCEPTION_OPEN", "ROLE_AWAITING_TWO_FACTOR")

        for (role in listOf(Role.BOARD, Role.TREASURER)) {
            assertThat(alertsOf(createUserWithRole(role)).map { it["kind"] }).doesNotContainAnyElementsOf(adminOnly)
        }
    }

    @Test
    fun `hiding an alert hides it for that person only, until they show it again`() {
        val first = createUserWithRole(Role.ADMIN)
        val second = createUserWithRole(Role.ADMIN)
        val key = "job-dead:${createJobExecutionFixture(status = JobExecutionStatus.DEAD).id}"

        post(first, "hidden", key).andExpect(status().isNoContent)

        assertThat(alertsOf(first).single { it["key"] == key }["hidden"]).isEqualTo(true)
        assertThat(alertsOf(second).single { it["key"] == key }["hidden"]).isEqualTo(false)

        post(first, "shown", key).andExpect(status().isNoContent)
        assertThat(alertsOf(first).single { it["key"] == key }["hidden"]).isEqualTo(false)
    }

    @Test
    fun `an alert the reader cannot see cannot be hidden, and members cannot read alerts`() {
        val board = createUserWithRole(Role.BOARD)
        val key = "job-dead:${createJobExecutionFixture(status = JobExecutionStatus.DEAD).id}"

        post(board, "hidden", key).andExpect(status().isNotFound)
        mvc.perform(get("/management/alerts").with(signedIn(createUserWithRole(Role.MEMBER)))).andExpect(status().isForbidden)
        mvc.perform(get("/management/alerts")).andExpect(status().isUnauthorized)
    }
}
