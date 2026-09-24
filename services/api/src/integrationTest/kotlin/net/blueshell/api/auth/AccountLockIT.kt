package net.blueshell.api.auth

import net.blueshell.api.auth.domain.SecurityEvents
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.auth.persistence.SecurityEventRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@SpringBootTest
class AccountLockIT : AccountSecurityTestSupport() {
    @Autowired
    private lateinit var securityEvents: SecurityEventRepository

    @Autowired
    private lateinit var events: SecurityEvents

    private fun changePassword(user: User) =
        mvc.perform(
            json(put("/users/me/password"), """{"currentPassword":"Password123!","newPassword":"Another123!"}""")
                .with(signedIn(user)),
        )

    @Nested
    inner class TheLockLink {
        @Test
        fun `a notification's lock link locks the account and ends its sign-ins, once`() {
            val member = createUserWithRole(Role.MEMBER)
            val admins = listOf(createUserWithRole(Role.ADMIN), createUserWithRole(Role.ADMIN))
            val elsewhere = signedIn(member)
            changePassword(member).andExpect(status().isNoContent)
            val link = lockLinks(member.id!!).single()

            mvc
                .perform(json(post("/recovery/lock"), """{"token":"$link"}"""))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.contactEmail").value("board@blueshell.utwente.nl"))

            mvc.perform(get("/users/${member.id}").with(elsewhere)).andExpect(status().isUnauthorized)
            passwordStep(member, "Another123!").andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("AccountLocked"))
            passwordStep(member, "Wrong123!").andExpect(status().isUnauthorized)
            assertThat(notices("ADMINISTRATOR").map { it.path("recipientUserId").asLong() }).containsAll(admins.map { it.id })
            assertThat(recoveryTokens.findBySelector(link.substringBefore(".")).get().consumedAt).isNotNull()
        }

        @Test
        fun `a used, expired or unknown link answers the same and locks nothing`() {
            val member = createUserWithRole(Role.MEMBER)
            changePassword(member)
            val link = lockLinks(member.id!!).single()

            clock.advance(Duration.ofHours(72).plusSeconds(1))
            mvc.perform(json(post("/recovery/lock"), """{"token":"$link"}""")).andExpect(status().isOk)
            mvc.perform(json(post("/recovery/lock"), """{"token":"nonsense.token"}""")).andExpect(status().isOk)

            passwordStep(member, "Another123!").andExpect(status().isOk)
        }

        @Test
        fun `a newer notification leaves an earlier lock link working`() {
            val member = createUserWithRole(Role.MEMBER)
            events.record(member.id!!, SecurityEventKind.PASSWORD_CHANGED)
            events.record(member.id!!, SecurityEventKind.BACKUP_CODES_REGENERATED)
            val (first, second) = lockLinks(member.id!!)

            assertThat(first).isNotEqualTo(second)
            mvc.perform(json(post("/recovery/lock"), """{"token":"$first"}"""))
            passwordStep(member).andExpect(jsonPath("$.code").value("AccountLocked"))
        }
    }

    @Nested
    inner class Unlocking {
        @Test
        fun `an admin unlocks with a reason, and the person is sent a password reset`() {
            val admin = createUserWithRole(Role.ADMIN)
            val member = createUserWithRole(Role.MEMBER)
            changePassword(member)
            mvc.perform(json(post("/recovery/lock"), """{"token":"${lockLinks(member.id!!).single()}"}"""))

            val unlock = { reason: String -> json(post("/users/{id}/unlock", member.id), """{"reason":"$reason"}""") }
            mvc.perform(unlock("").with(signedIn(admin, steppedUp = true))).andExpect(status().isBadRequest)
            mvc.perform(unlock("spoke on Discord").with(signedIn(member))).andExpect(status().isUnauthorized)
            mvc
                .perform(unlock("heard from them in person").with(signedIn(admin)))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("StepUpRequired"))
            mvc
                .perform(unlock("heard from them in person").with(signedIn(admin, steppedUp = true)))
                .andExpect(status().isNoContent)

            assertThat(recoveryLink(member.id!!, TokenPurpose.PASSWORD_RESET)).isNotBlank()
            val unlocked = securityEvents.findAll().first { it.kind == SecurityEventKind.ACCOUNT_UNLOCKED }
            assertThat(unlocked.note).isEqualTo("heard from them in person")
            mvc
                .perform(unlock("again").with(signedIn(admin, steppedUp = true)))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("NotLocked"))
        }

        @Test
        fun `somebody who is not an admin cannot unlock`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)

            mvc
                .perform(json(post("/users/{id}/unlock", member.id), """{"reason":"please"}""").with(signedIn(board)))
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class ChangingEmail {
        @Test
        fun `the address moves only once the new inbox confirms it, and the old one is told first`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc
                .perform(json(post("/users/me/email"), """{"email":"moved@example.com"}""").with(signedIn(member)))
                .andExpect(jsonPath("$.code").value("StepUpRequired"))
            mvc
                .perform(json(post("/users/me/email"), """{"email":"moved@example.com"}""").with(signedIn(member, steppedUp = true)))
                .andExpect(status().isNoContent)

            assertThat(refreshUser(member).email).isEqualTo(member.email)
            assertThat(lockLinks(member.id!!)).hasSize(1)

            mvc
                .perform(json(post("/recovery/email/confirm"), """{"token":"${recoveryLink(member.id!!, TokenPurpose.EMAIL_CHANGE)}"}"""))
                .andExpect(status().isNoContent)
            assertThat(refreshUser(member).email).isEqualTo("moved@example.com")
        }

        @Test
        fun `locking from the old address cancels a move not yet confirmed`() {
            val member = createUserWithRole(Role.MEMBER)
            mvc.perform(json(post("/users/me/email"), """{"email":"thief@example.com"}""").with(signedIn(member, steppedUp = true)))
            val confirmation = recoveryLink(member.id!!, TokenPurpose.EMAIL_CHANGE)

            mvc.perform(json(post("/recovery/lock"), """{"token":"${lockLinks(member.id!!).single()}"}"""))

            mvc.perform(json(post("/recovery/email/confirm"), """{"token":"$confirmation"}""")).andExpect(status().isBadRequest)
            assertThat(refreshUser(member).email).isEqualTo(member.email)
        }

        @Test
        fun `an address that belongs to somebody else is refused`() {
            val member = createUserWithRole(Role.MEMBER)
            val other = createUserWithRole(Role.MEMBER)

            mvc
                .perform(json(post("/users/me/email"), """{"email":"${other.email}"}""").with(signedIn(member, steppedUp = true)))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("EmailTaken"))
        }
    }

    @Nested
    inner class TheSecurityLog {
        @Test
        fun `a password reset writes one row and sends one notification`() {
            val member = createUserWithRole(Role.MEMBER)
            mvc.perform(post("/recovery/password/reset/${member.username}"))
            val token = recoveryLink(member.id!!, TokenPurpose.PASSWORD_RESET)

            mvc.perform(json(post("/recovery/password"), """{"token":"$token","password":"Another123!"}""")).andExpect(status().isNoContent)

            assertThat(securityEvents.findAll().filter { it.kind == SecurityEventKind.PASSWORD_RESET }).hasSize(1)
            assertThat(lockLinks(member.id!!)).hasSize(1)
        }

        @Test
        fun `a person reads their own log, an admin reads anybody's, and nobody else reads either`() {
            val admin = createUserWithRole(Role.ADMIN)
            val member = createUserWithRole(Role.MEMBER)
            val other = createUserWithRole(Role.MEMBER)
            events.record(member.id!!, SecurityEventKind.PASSWORD_CHANGED)

            mvc.perform(get("/users/me/security-events").with(signedIn(member))).andExpect(jsonPath("$.events.length()").value(1))
            mvc.perform(get("/users/me/security-events").with(signedIn(other))).andExpect(jsonPath("$.events.length()").value(0))
            mvc
                .perform(get("/users/{id}/security-events", member.id).with(signedIn(admin)))
                .andExpect(jsonPath("$.events[0].kind").value("PASSWORD_CHANGED"))
            mvc.perform(get("/users/{id}/security-events", member.id).with(signedIn(other))).andExpect(status().isForbidden)
        }

        @Test
        fun `rows older than twelve months are purged and younger ones kept`() {
            val member = createUserWithRole(Role.MEMBER)
            events.record(member.id!!, SecurityEventKind.SIGNED_OUT_EVERYWHERE)
            clock.advance(Duration.ofDays(200))
            events.record(member.id!!, SecurityEventKind.SIGNED_OUT_EVERYWHERE)
            clock.advance(Duration.ofDays(166))

            assertThat(transactionTemplate.execute { events.purgeExpired() }).isEqualTo(1)
            assertThat(securityEvents.findAll()).hasSize(1)
        }
    }
}
