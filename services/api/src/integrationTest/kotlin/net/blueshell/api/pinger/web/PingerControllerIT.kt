package net.blueshell.api.pinger.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The one paint-job row is a singleton these tests edit in place, with no rollback between them, so
 * every test sets the state it then asserts rather than leaning on the seed or on another test.
 */
@SpringBootTest
class PingerControllerIT : UserTestSupport() {
    @Test
    fun `an admin sets the paint job and anyone may read it back`() {
        val admin = createUserWithRole(Role.ADMIN)
        mvc
            .perform(
                put("/pinger/paint")
                    .with(signedIn(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"prefix":"2001:db8:b317:a000::/64","ratePps":256,"originX":100,""" +
                            """"originY":200,"width":800,"height":600,"siteCieEnabled":false}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.ratePps").value(256))
            .andExpect(jsonPath("$.siteCieEnabled").value(false))

        // The read is public: no session on this request.
        mvc
            .perform(get("/pinger/paint"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.prefix").value("2001:db8:b317:a000::/64"))
            .andExpect(jsonPath("$.originX").value(100))
            .andExpect(jsonPath("$.width").value(800))
            .andExpect(jsonPath("$.siteCieEnabled").value(false))
    }

    @Test
    fun `a box that runs off the canvas is refused`() {
        val admin = createUserWithRole(Role.ADMIN)
        mvc
            .perform(
                put("/pinger/paint")
                    .with(signedIn(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"ratePps":128,"originX":3800,"originY":100,"width":800,"height":600}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `a member may not change the paint job`() {
        val member = createUserWithRole(Role.MEMBER)
        mvc
            .perform(
                put("/pinger/paint")
                    .with(signedIn(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"ratePps":128,"originX":0,"originY":0,"width":100,"height":100}"""),
            ).andExpect(status().isForbidden)
    }
}
