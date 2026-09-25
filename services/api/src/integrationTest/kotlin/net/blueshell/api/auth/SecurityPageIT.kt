package net.blueshell.api.auth

import net.blueshell.api.shared.enums.Role
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@SpringBootTest
class SecurityPageIT : AccountSecurityTestSupport() {
    @Test
    fun `changing the password asks for the current one and ends the other sign-ins`() {
        val member = createUserWithRole(Role.MEMBER)
        val here = signedIn(member)
        val elsewhere = signedIn(member)

        mvc
            .perform(json(put("/users/me/password"), """{"currentPassword":"Wrong123!","newPassword":"Another123!"}""").with(here))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("WrongPassword"))
        mvc
            .perform(json(put("/users/me/password"), """{"currentPassword":"Password123!","newPassword":"Another123!"}""").with(here))
            .andExpect(status().isNoContent)

        mvc.perform(get("/users/me/sign-ins").with(here)).andExpect(jsonPath("$.length()").value(1))
        mvc.perform(get("/users/me/sign-ins").with(elsewhere)).andExpect(status().isUnauthorized)
        passwordStep(member, "Another123!").andExpect(status().isOk)
    }

    @Test
    fun `with two-factor on, changing the password also asks for a step-up`() {
        val member = createUserWithRole(Role.MEMBER)
        enrol(member)

        mvc
            .perform(
                json(put("/users/me/password"), """{"currentPassword":"Password123!","newPassword":"Another123!"}""")
                    .with(signedIn(member)),
            )
            .andExpect(jsonPath("$.code").value("StepUpRequired"))
    }

    @Test
    fun `the address reads with a move to another one that is still waiting`() {
        val member = createUserWithRole(Role.MEMBER)
        val here = signedIn(member, steppedUp = true)

        mvc
            .perform(get("/users/me/email").with(here))
            .andExpect(jsonPath("$.email").value(member.email))
            .andExpect(jsonPath("$.pendingEmail").doesNotExist())

        mvc.perform(json(post("/users/me/email"), """{"email":"Moving@Example.com"}""").with(here)).andExpect(status().isNoContent)

        mvc
            .perform(get("/users/me/email").with(here))
            .andExpect(jsonPath("$.email").value(member.email))
            .andExpect(jsonPath("$.pendingEmail").value("moving@example.com"))
    }

    @Test
    fun `the two-factor standing says since when it is on`() {
        val member = createUserWithRole(Role.MEMBER)
        mvc.perform(get("/users/me/two-factor").with(signedIn(member))).andExpect(jsonPath("$.since").doesNotExist())

        val turnedOn = clock.instant()
        enrol(member)

        mvc
            .perform(get("/users/me/two-factor").with(signedIn(member)))
            .andExpect(jsonPath("$.since").value(turnedOn.toString()))
    }

    @Test
    fun `the sign-ins are listed with this one marked, and end one at a time or all at once`() {
        val member = createUserWithRole(Role.MEMBER)
        val here = signedIn(member)
        clock.advance(Duration.ofMinutes(1))
        signedIn(member)
        val other = signIns.of(member.id!!).first()

        mvc
            .perform(get("/users/me/sign-ins").with(here))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.current == true)]", hasSize<Any>(1)))

        mvc.perform(delete("/users/me/sign-ins/{id}", other.id).with(here)).andExpect(status().isNoContent)
        mvc.perform(get("/users/me/sign-ins").with(here)).andExpect(jsonPath("$.length()").value(1))

        mvc.perform(delete("/users/me/sign-ins").with(here)).andExpect(status().isNoContent)
        mvc.perform(get("/users/me/sign-ins").with(here)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `signing out everywhere else leaves only this sign-in`() {
        val member = createUserWithRole(Role.MEMBER)
        val here = passwordStep(member).andReturn().authCookie!!
        val elsewhere = passwordStep(member).andReturn().authCookie!!

        mvc.perform(delete("/users/me/sign-ins/others").cookie(here)).andExpect(status().isNoContent)

        mvc.perform(get("/users/me/sign-ins").cookie(here)).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1))
        mvc.perform(get("/users/me/sign-ins").cookie(elsewhere)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `somebody else's sign-in cannot be ended`() {
        val member = createUserWithRole(Role.MEMBER)
        val other = createUserWithRole(Role.MEMBER)
        signedIn(other)

        mvc
            .perform(delete("/users/me/sign-ins/{id}", signIns.of(other.id!!).single().id).with(signedIn(member)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `trusted browsers are listed and forgotten`() {
        val member = createUserWithRole(Role.MEMBER)
        val key = enrol(member)
        codeStep(passwordStep(member).andReturn().challengeCookie, codeFor(key), trustThisBrowser = true)

        mvc.perform(get("/users/me/trusted-browsers").with(signedIn(member))).andExpect(jsonPath("$.length()").value(1))
        mvc.perform(delete("/users/me/trusted-browsers").with(signedIn(member))).andExpect(status().isNoContent)
        mvc.perform(get("/users/me/trusted-browsers").with(signedIn(member))).andExpect(jsonPath("$.length()").value(0))
    }
}
