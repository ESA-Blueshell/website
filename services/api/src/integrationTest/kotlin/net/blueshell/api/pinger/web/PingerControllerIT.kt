package net.blueshell.api.pinger.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The one paint-job settings row is a singleton these tests edit in place, with no rollback between
 * them, so every test sets the state it then asserts. The placements are edited through their own
 * endpoints; adding one needs a stored image, which the service refuses when it is not in storage,
 * so the add-path happy case is covered by the service unit test and these cover auth and bounds.
 */
@SpringBootTest
class PingerControllerIT : UserTestSupport() {
    @Test
    fun `an admin sets the settings and anyone may read them back with the placements`() {
        val admin = createUserWithRole(Role.ADMIN)
        mvc
            .perform(
                put("/pinger/paint/settings")
                    .with(signedIn(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"prefix":"2001:db8:b317:a000::/64","ratePps":256,"siteCieEnabled":false}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.ratePps").value(256))
            .andExpect(jsonPath("$.siteCieEnabled").value(false))

        // The read is public: no session on this request.
        mvc
            .perform(get("/pinger/paint"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.prefix").value("2001:db8:b317:a000::/64"))
            .andExpect(jsonPath("$.siteCieEnabled").value(false))
            .andExpect(jsonPath("$.placements").isArray)
    }

    @Test
    fun `a placement box that runs off the canvas is refused`() {
        val admin = createUserWithRole(Role.ADMIN)
        mvc
            .perform(
                post("/pinger/paint/placements")
                    .with(signedIn(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"imagePath":"pinger-paint/whatever.webp","originX":3800,"originY":100,"width":800,"height":600}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `a member may not change the settings`() {
        val member = createUserWithRole(Role.MEMBER)
        mvc
            .perform(
                put("/pinger/paint/settings")
                    .with(signedIn(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"ratePps":128,"siteCieEnabled":true}"""),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `a member may not add a placement`() {
        val member = createUserWithRole(Role.MEMBER)
        mvc
            .perform(
                post("/pinger/paint/placements")
                    .with(signedIn(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"imagePath":"pinger-paint/a.webp","originX":0,"originY":0,"width":100,"height":100}"""),
            ).andExpect(status().isForbidden)
    }
}
