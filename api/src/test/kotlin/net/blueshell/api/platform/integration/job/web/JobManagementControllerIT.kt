package net.blueshell.api.platform.integration.job.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class JobManagementControllerIT : UserTestSupport() {

    @Nested
    inner class List {
        @Test
        fun `admin lists recent jobs`() {
            val admin = createUserWithRole(Role.ADMIN)
            val first = createJobExecutionFixture(jobType = "job.a")
            val second = createJobExecutionFixture(jobType = "job.b")

            mvc.perform(get("/management/jobs").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").isNumber)
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.hasItems(first.id!!.toInt(), second.id!!.toInt())))
        }
    }

    @Nested
    inner class Retry {
        @Test
        fun `admin retries job`() {
            val admin = createUserWithRole(Role.ADMIN)
            val job = createJobExecutionFixture(jobType = "retry-target")

            mvc.perform(post("/management/jobs/{id}/retry", job.id).with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(job.id))
                .andExpect(jsonPath("$.jobType").value("retry-target"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.attempts").value(1))

            val reloaded = jobExecutions.findById(job.id!!).orElseThrow()
            assertThat(reloaded.attempts).isEqualTo(1)
        }
    }
}
