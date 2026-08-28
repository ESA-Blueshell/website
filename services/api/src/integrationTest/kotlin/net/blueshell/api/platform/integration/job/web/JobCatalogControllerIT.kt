package net.blueshell.api.jobs.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Exercises the manual job-trigger catalog against the real application context.
 * The handlers are @Transactional (CGLIB-proxied), so this is the layer that
 * catches the proxy-vs-payloadType regression a mocked unit test cannot.
 */
@SpringBootTest
class JobCatalogControllerIT : UserTestSupport() {

    @Nested
    inner class Types {
        @Test
        fun `lists registered job types with their reflected payload fields`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(get("/management/jobs/types").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[*].type").value(hasItem("contact.sync")))
                .andExpect(jsonPath("$[*].type").value(hasItem("email.recovery")))
                // The payload fields must actually be reflected, not empty: a
                // CGLIB-proxied handler with a final payloadType getter regressed
                // this to [] for every type.
                .andExpect(jsonPath("$..payloadFields[*].name").value(hasItem("userId")))
        }
    }

    @Nested
    inner class Enqueue {
        @Test
        fun `admin enqueues a job by type and payload`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                post("/management/jobs/enqueue")
                    .with(bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"jobType":"contact.sync","payload":{"userId":${admin.id}}}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.jobType").value("contact.sync"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
        }

        @Test
        fun `enqueue rejects an unknown job type`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                post("/management/jobs/enqueue")
                    .with(bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"jobType":"does.not.exist","payload":{}}""")
            )
                .andExpect(status().isBadRequest)
        }
    }
}
