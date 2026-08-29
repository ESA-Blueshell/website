package net.blueshell.api.user

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.sync.domain.SyncAllContactsJob
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The account that owns the files the site ships with.
 *
 * It exists so that art nobody chose is not credited to a board member. It is the site rather
 * than a person: nobody signs in as it, nothing mails it, and it is not somebody a listing
 * offers or a total counts.
 *
 * That it is here at all is also the proof of the last acceptance criterion — the wipe between
 * tests puts it back, so a test that ran before this one cannot have taken it away.
 */
@SpringBootTest
class ServiceAccountIT : UserTestSupport() {
    @Autowired
    private lateinit var fanOut: SyncAllContactsJob

    @MockitoBean
    private lateinit var jobs: TrackedJobDispatcher

    private fun serviceAccount(): User =
        userRepository.findAll().single { it.hasRole(Role.SYSTEM) }

    @Test
    fun `an account holding the system role exists, disabled, with a password no sign-in can satisfy`() {
        val account = serviceAccount()

        assertThat(account.enabled).isFalse()
        assertThat(passwordEncoder.matches("", account.password)).isFalse()
        assertThat(passwordEncoder.matches(account.password, account.password)).isFalse()
        // Inherits administrator, so it carries the role's mitigations rather than being an
        // exception nobody remembers to check.
        assertThat(account.hasAuthority(Role.ADMIN)).isTrue()
    }

    /**
     * The refusal is the role, not the state of the row. Enabling the account and giving it a
     * password anybody knows is the exact thing that must not turn it into a live administrator.
     */
    @Test
    fun `the authentication path refuses it even once enabled and given a working password`() {
        val account = serviceAccount()
        account.enabled = true
        account.password = passwordEncoder.encode("letmein")!!
        userRepository.save(account)

        mvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("username" to account.username, "password" to "letmein")))
                .with(csrfToken()),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `the contact fan-out leaves it out`() {
        val member = createUserWithRole(Role.MEMBER)

        fanOut.handle(mapper.writeValueAsString(ContactJobs.SyncAllContactsPayload()), null)

        verify(jobs).runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(member.id!!)))
        verify(jobs, never()).runAsync(
            eq(ContactJobs.SyncContact),
            eq(ContactJobs.SyncContactPayload(serviceAccount().id!!)),
        )
    }

    @Test
    fun `it is absent from the user listing and from what the listing counts`() {
        val admin = createUserWithRole(Role.ADMIN)

        val result = mvc.perform(get("/users?size=200").with(bearer(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[?(@.id == ${serviceAccount().id})]").isEmpty)
            .andReturn()

        val body = mapper.readTree(result.response.contentAsString)
        assertThat(body["page"]["totalElements"].asInt()).isEqualTo(body["content"].size())
    }
}
