package net.blueshell.api.auth

import jakarta.servlet.http.Cookie
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.auth.persistence.SecurityEventRepository
import net.blueshell.api.auth.persistence.TwoFactorSecretRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@SpringBootTest
class TwoFactorIT : AccountSecurityTestSupport() {
    @Autowired
    private lateinit var secrets: TwoFactorSecretRepository

    @Autowired
    private lateinit var securityEvents: SecurityEventRepository

    @Nested
    inner class SettingUp {
        @Test
        fun `a wrong password shows no secret`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc
                .perform(json(post("/users/me/two-factor/setup"), """{"password":"nope"}""").with(signedIn(member)))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("WrongPassword"))
        }

        @Test
        fun `two-factor is on only once the backup codes are saved`() {
            val member = createUserWithRole(Role.MEMBER)
            val setUp =
                mvc
                    .perform(json(post("/users/me/two-factor/setup"), """{"password":"Password123!"}""").with(signedIn(member)))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.otpauthUri").value(startsWith("otpauth://totp/ESA%20Blueshell:")))
                    .andReturn()
            val key = mapper.readTree(setUp.response.contentAsString).path("key").asString()

            mvc
                .perform(json(post("/users/me/two-factor/confirm"), """{"code":"000000"}""").with(signedIn(member)))
                .andExpect(status().isUnauthorized)
            mvc
                .perform(json(post("/users/me/two-factor/confirm"), """{"code":"${codeFor(key)}"}""").with(signedIn(member)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.codes.length()").value(10))
            mvc.perform(get("/users/me/two-factor").with(signedIn(member))).andExpect(jsonPath("$.on").value(false))

            mvc.perform(post("/users/me/two-factor/saved").with(signedIn(member))).andExpect(status().isNoContent)

            mvc
                .perform(get("/users/me/two-factor").with(signedIn(member)))
                .andExpect(jsonPath("$.on").value(true))
                .andExpect(jsonPath("$.backupCodesLeft").value(10))
        }

        @Test
        fun `the database never holds a readable secret`() {
            val member = createUserWithRole(Role.MEMBER)
            val key = enrol(member)

            val stored = secrets.findAll().single()
            assertThat(stored.ciphertext).doesNotContain(key)
            assertThat(stored.keyId).isEqualTo("1")
        }

        @Test
        fun `turning it on ends the other sign-ins and keeps this one`() {
            val member = createUserWithRole(Role.MEMBER)
            val elsewhere = signedIn(member)
            val here = signedIn(member, steppedUp = true)
            val setUp =
                mvc.perform(json(post("/users/me/two-factor/setup"), """{"password":"Password123!"}""").with(here)).andReturn()
            val key = mapper.readTree(setUp.response.contentAsString).path("key").asString()
            mvc.perform(json(post("/users/me/two-factor/confirm"), """{"code":"${codeFor(key)}"}""").with(here))

            mvc.perform(post("/users/me/two-factor/saved").with(here)).andExpect(status().isNoContent)

            mvc.perform(get("/users/me/two-factor").with(here)).andExpect(status().isOk)
            mvc.perform(get("/users/me/two-factor").with(elsewhere)).andExpect(status().isUnauthorized)
            assertThat(securityEvents.findAll().map { it.kind }).contains(SecurityEventKind.TWO_FACTOR_ON)
            assertThat(notices("PERSON")).isNotEmpty
        }
    }

    @Nested
    inner class SigningIn {
        @Test
        fun `a right password is only a challenge, and the challenge signs nothing in`() {
            val member = createUserWithRole(Role.MEMBER)
            enrol(member)

            val password =
                passwordStep(member)
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("TWO_FACTOR_REQUIRED"))
                    .andReturn()

            assertThat(password.authCookie).isNull()
            val challenge = password.challengeCookie
            mvc.perform(get("/users/${member.id}").cookie(challenge)).andExpect(status().isUnauthorized)
            mvc
                .perform(get("/users/${member.id}").cookie(Cookie("BSH_AUTH", challenge.value)))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `a right code signs in, and the same code cannot sign in twice`() {
            val member = createUserWithRole(Role.MEMBER)
            val key = enrol(member)
            val code = codeFor(key)

            val signedIn =
                codeStep(passwordStep(member).andReturn().challengeCookie, code)
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.login.twoFactor.on").value(true))
                    .andReturn()
            mvc.perform(get("/users/${member.id}").cookie(signedIn.authCookie!!)).andExpect(status().isOk)

            codeStep(passwordStep(member).andReturn().challengeCookie, code)
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("WrongCode"))
        }

        @Test
        fun `a challenge dies after five wrong codes, and a late right one finds nothing`() {
            val member = createUserWithRole(Role.MEMBER)
            val key = enrol(member)
            val challenge = passwordStep(member).andReturn().challengeCookie

            for (left in 4 downTo 0) {
                codeStep(challenge, "000000").andExpect(jsonPath("$.triesLeft").value(left))
            }

            codeStep(challenge, codeFor(key))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("ChallengeExpired"))
        }

        @Test
        fun `a challenge lasts five minutes`() {
            val member = createUserWithRole(Role.MEMBER)
            val key = enrol(member)
            val challenge = passwordStep(member).andReturn().challengeCookie

            clock.advance(Duration.ofMinutes(5))

            codeStep(challenge, codeFor(key)).andExpect(jsonPath("$.code").value("ChallengeExpired"))
        }

        @Test
        fun `ten wrong codes in fifteen minutes stop every code for the account and tell the owner`() {
            val member = createUserWithRole(Role.MEMBER)
            val key = enrol(member)
            repeat(2) {
                val challenge = passwordStep(member).andReturn().challengeCookie
                repeat(5) { codeStep(challenge, "000000") }
            }

            codeStep(passwordStep(member).andReturn().challengeCookie, codeFor(key))
                .andExpect(status().isTooManyRequests)
                .andExpect(jsonPath("$.code").value("CodeLimitReached"))
            assertThat(securityEvents.findAll().map { it.kind }).contains(SecurityEventKind.CODE_LIMIT_REACHED)

            clock.advance(Duration.ofMinutes(15))
            nextStep()
            codeStep(passwordStep(member).andReturn().challengeCookie, codeFor(key)).andExpect(status().isOk)
        }

        @Test
        fun `a backup code signs in once`() {
            val member = createUserWithRole(Role.MEMBER)
            val setUp =
                mvc
                    .perform(json(post("/users/me/two-factor/setup"), """{"password":"Password123!"}""").with(signedIn(member)))
                    .andReturn()
            val key = mapper.readTree(setUp.response.contentAsString).path("key").asString()
            val codes =
                mapper
                    .readTree(
                        mvc
                            .perform(json(post("/users/me/two-factor/confirm"), """{"code":"${codeFor(key)}"}""").with(signedIn(member)))
                            .andReturn()
                            .response.contentAsString,
                    ).path("codes")
            mvc.perform(post("/users/me/two-factor/saved").with(signedIn(member)))
            val backup = codes[0].asString()

            codeStep(passwordStep(member).andReturn().challengeCookie, backup).andExpect(status().isOk)
            codeStep(passwordStep(member).andReturn().challengeCookie, backup).andExpect(status().isUnauthorized)
            mvc.perform(get("/users/me/two-factor").with(signedIn(member))).andExpect(jsonPath("$.backupCodesLeft").value(9))
        }

        @Test
        fun `a trusted browser skips the code for thirty days from being trusted`() {
            val member = createUserWithRole(Role.MEMBER)
            val key = enrol(member)
            val trusted =
                codeStep(passwordStep(member).andReturn().challengeCookie, codeFor(key), trustThisBrowser = true)
                    .andReturn()
                    .cookie("BSH_TRUSTED_BROWSER")!!

            val again = passwordStep(member, cookies = arrayOf(trusted)).andExpect(jsonPath("$.status").value("SIGNED_IN")).andReturn()
            val rotated = again.cookie("BSH_TRUSTED_BROWSER")!!
            assertThat(rotated.value).isNotEqualTo(trusted.value)
            passwordStep(member, cookies = arrayOf(trusted)).andExpect(jsonPath("$.status").value("TWO_FACTOR_REQUIRED"))

            clock.advance(Duration.ofDays(30))
            passwordStep(member, cookies = arrayOf(rotated)).andExpect(jsonPath("$.status").value("TWO_FACTOR_REQUIRED"))
        }
    }

    @Nested
    inner class StepUp {
        @Test
        fun `turning two-factor off asks for a fresh code, and a code gives one`() {
            val member = createUserWithRole(Role.MEMBER)
            val key = enrol(member)
            val signIn = signedIn(member)

            mvc.perform(delete("/users/me/two-factor").with(signIn)).andExpect(jsonPath("$.code").value("StepUpRequired"))
            mvc.perform(json(post("/auth/step-up"), """{"code":"${codeFor(key)}"}""").with(signIn)).andExpect(status().isNoContent)
            mvc.perform(delete("/users/me/two-factor").with(signIn)).andExpect(status().isNoContent)

            mvc.perform(get("/users/me/two-factor").with(signIn)).andExpect(jsonPath("$.on").value(false))
        }

        @Test
        fun `a step-up lasts ten minutes`() {
            val member = createUserWithRole(Role.MEMBER)
            enrol(member)
            val signIn = signedIn(member, steppedUp = true)

            clock.advance(Duration.ofMinutes(10).plusSeconds(1))

            mvc.perform(post("/users/me/two-factor/backup-codes").with(signIn)).andExpect(jsonPath("$.code").value("StepUpRequired"))
        }

        @Test
        fun `somebody holding a granted role may replace two-factor but never turn it off`() {
            val board = createUserWithRole(Role.MEMBER)
            enrol(board)
            val promoted = refreshUser(board).also { it.roles = mutableSetOf(Role.BOARD) }
            persist(promoted)

            mvc
                .perform(delete("/users/me/two-factor").with(signedIn(promoted, steppedUp = true)))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("TwoFactorRequired"))
        }

        @Test
        fun `regenerating backup codes kills every earlier one`() {
            val member = createUserWithRole(Role.MEMBER)
            val (_, earlier) = enrolWithBackupCodes(member)

            val fresh =
                mvc
                    .perform(post("/users/me/two-factor/backup-codes").with(signedIn(member, steppedUp = true)))
                    .andExpect(jsonPath("$.codes.length()").value(10))
                    .andReturn()

            codeStep(passwordStep(member).andReturn().challengeCookie, earlier[0]).andExpect(status().isUnauthorized)
            val code = mapper.readTree(fresh.response.contentAsString).path("codes")[0].asString()
            codeStep(passwordStep(member).andReturn().challengeCookie, code).andExpect(status().isOk)
        }

        @Test
        fun `replacing keeps the old app working until the new codes are saved, then kills it and its backup codes`() {
            val member = createUserWithRole(Role.MEMBER)
            val (oldKey, oldCodes) = enrolWithBackupCodes(member)
            val setUp =
                mvc
                    .perform(
                        json(post("/users/me/two-factor/setup"), """{"password":"Password123!"}""")
                            .with(signedIn(member, steppedUp = true)),
                    ).andReturn()
            val newKey = mapper.readTree(setUp.response.contentAsString).path("key").asString()

            codeStep(passwordStep(member).andReturn().challengeCookie, codeFor(oldKey)).andExpect(status().isOk)
            nextStep()
            mvc.perform(json(post("/users/me/two-factor/confirm"), """{"code":"${codeFor(newKey)}"}""").with(signedIn(member)))
            mvc.perform(post("/users/me/two-factor/saved").with(signedIn(member))).andExpect(status().isNoContent)
            nextStep()

            codeStep(passwordStep(member).andReturn().challengeCookie, codeFor(oldKey)).andExpect(status().isUnauthorized)
            codeStep(passwordStep(member).andReturn().challengeCookie, oldCodes[0]).andExpect(status().isUnauthorized)
            codeStep(passwordStep(member).andReturn().challengeCookie, codeFor(newKey)).andExpect(status().isOk)
            assertThat(securityEvents.findAll().map { it.kind }).contains(SecurityEventKind.TWO_FACTOR_REPLACED)
        }

        @Test
        fun `without two-factor the password is the step-up`() {
            val member = createUserWithRole(Role.MEMBER)
            val signIn = signedIn(member)

            mvc.perform(json(post("/auth/step-up"), """{"password":"wrong"}""").with(signIn)).andExpect(status().isForbidden)
            mvc.perform(json(post("/auth/step-up"), """{"password":"Password123!"}""").with(signIn)).andExpect(status().isNoContent)
        }
    }

    @Nested
    inner class DormantRoles {
        @Test
        fun `a granted role without two-factor allows nothing, and allows everything once it is set up`() {
            val admin = createUserWithRole(Role.ADMIN, twoFactor = false)
            val member = createUserWithRole(Role.MEMBER)
            val grant = json(put("/users/{id}/roles", member.id), """{"roles":["BOARD"]}""")

            mvc.perform(grant.with(signedIn(admin))).andExpect(status().isForbidden)
            mvc
                .perform(get("/users/me/two-factor").with(signedIn(admin)))
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.offered").value(false))

            enrol(admin)

            mvc.perform(json(put("/users/{id}/roles", member.id), """{"roles":["BOARD"]}""").with(signedIn(admin))).andExpect(status().isOk)
            mvc
                .perform(get("/users/{id}", admin.id).with(signedIn(admin)))
                .andExpect(jsonPath("$.twoFactorOn").value(true))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.awaitingReenrolment").value(false))
        }

        @Test
        fun `granting a role to somebody without two-factor succeeds and leaves it dormant`() {
            val admin = createUserWithRole(Role.ADMIN)
            val member = createUserWithRole(Role.MEMBER)

            mvc
                .perform(json(put("/users/{id}/roles", member.id), """{"roles":["BOARD"]}""").with(signedIn(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.dormant[0]").value("BOARD"))

            mvc.perform(get("/users").with(signedIn(member))).andExpect(status().isForbidden)
            val granted = securityEvents.findAll().single { it.kind == SecurityEventKind.ROLES_CHANGED }
            assertThat(granted.subject.id).isEqualTo(member.id)
            assertThat(granted.actor?.id).isEqualTo(admin.id)
        }
    }

    @Nested
    inner class TheHandshake {
        @Test
        fun `a role granted to somebody without two-factor ends every sign-in they hold, and the email says to sign in again`() {
            val admin = createUserWithRole(Role.ADMIN)
            val member = createUserWithRole(Role.MEMBER)
            val here = passwordStep(member).andReturn().authCookie!!
            val elsewhere = passwordStep(member).andReturn().authCookie!!

            mvc
                .perform(json(put("/users/{id}/roles", member.id), """{"roles":["TREASURER"]}""").with(signedIn(admin)))
                .andExpect(status().isOk)

            mvc.perform(get("/users/me/two-factor").cookie(here)).andExpect(status().isUnauthorized)
            mvc.perform(get("/users/me/two-factor").cookie(elsewhere)).andExpect(status().isUnauthorized)
            val logged = securityEvents.findAll().filter { it.subject.id == member.id }
            assertThat(logged.map { it.kind }).contains(SecurityEventKind.ROLES_CHANGED, SecurityEventKind.SIGNED_OUT_EVERYWHERE)
            assertThat(logged.single { it.kind == SecurityEventKind.SIGNED_OUT_EVERYWHERE }.actor?.id).isEqualTo(admin.id)
            assertThat(findJobsByType(EmailJobs.RoleChange.type)).hasSize(1)

            val next = passwordStep(member).andReturn().authCookie!!
            mvc.perform(json(post("/users/me/two-factor/setup"), "{}").cookie(next)).andExpect(status().isOk)
        }

        @Test
        fun `a role granted to somebody with two-factor ends none`() {
            val admin = createUserWithRole(Role.ADMIN)
            val member = createUserWithRole(Role.MEMBER)
            enrol(member)
            val here = signedIn(member)

            mvc.perform(json(put("/users/{id}/roles", member.id), """{"roles":["BOARD"]}""").with(signedIn(admin))).andExpect(status().isOk)

            mvc.perform(get("/users/me/two-factor").with(here)).andExpect(status().isOk)
            assertThat(securityEvents.findAll().map { it.kind }).doesNotContain(SecurityEventKind.SIGNED_OUT_EVERYWHERE)
        }
    }

    @Nested
    inner class StraightAfterSigningIn {
        @Test
        fun `a granted role waiting on two-factor sets up without the password, on the sign-in it just made`() {
            val board = createUserWithRole(Role.BOARD, twoFactor = false)
            val here = passwordStep(board).andExpect(status().isOk).andReturn().authCookie!!

            val setUp = mvc.perform(json(post("/users/me/two-factor/setup"), "{}").cookie(here)).andExpect(status().isOk).andReturn()
            val key = mapper.readTree(setUp.response.contentAsString).path("key").asString()
            mvc.perform(json(post("/users/me/two-factor/confirm"), """{"code":"${codeFor(key)}"}""").cookie(here)).andExpect(status().isOk)
            mvc.perform(post("/users/me/two-factor/saved").cookie(here)).andExpect(status().isNoContent)

            mvc.perform(get("/users/me/two-factor").cookie(here)).andExpect(jsonPath("$.on").value(true))
            mvc.perform(get("/users").cookie(here)).andExpect(status().isOk)
        }

        @Test
        fun `the proof lasts the step-up window, and after it the set-up asks for the password`() {
            val board = createUserWithRole(Role.BOARD, twoFactor = false)
            val here = passwordStep(board).andReturn().authCookie!!
            clock.advance(Duration.ofMinutes(11))

            mvc
                .perform(json(post("/users/me/two-factor/setup"), "{}").cookie(here))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("StepUpRequired"))
            mvc
                .perform(json(post("/users/me/two-factor/setup"), """{"password":"Password123!"}""").cookie(here))
                .andExpect(status().isOk)
        }

        @Test
        fun `a member's sign-in proves nothing, so a member always gives the password`() {
            val member = createUserWithRole(Role.MEMBER)
            val here = passwordStep(member).andReturn().authCookie!!

            mvc
                .perform(json(post("/users/me/two-factor/setup"), "{}").cookie(here))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("WrongPassword"))
        }
    }

    @Nested
    inner class TheOffer {
        @Test
        fun `the offer is made once per person, whichever way it is answered`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(get("/users/me/two-factor").with(signedIn(member))).andExpect(jsonPath("$.offered").value(true))
            mvc.perform(post("/users/me/two-factor/offer").with(signedIn(member))).andExpect(status().isNoContent)

            passwordStep(member).andExpect(jsonPath("$.login.twoFactor.offered").value(false))
        }
    }
}
