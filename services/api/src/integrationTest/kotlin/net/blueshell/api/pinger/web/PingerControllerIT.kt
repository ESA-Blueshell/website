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

@SpringBootTest
class PingerControllerIT : UserTestSupport() {
    @Test
    fun `anyone reads the seeded paint job`() {
        mvc
            .perform(get("/pinger/paint"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ratePps").value(128))
            .andExpect(jsonPath("$.width").value(900))
            .andExpect(jsonPath("$.imageUrl").doesNotExist())
    }

    @Test
    fun `an admin sets the prefix and the box, and the public read reflects it`() {
        val admin = createUserWithRole(Role.ADMIN)
        mvc
            .perform(
                put("/pinger/paint")
                    .with(signedIn(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"prefix":"2001:db8:b317:a000::/64","ratePps":256,"originX":100,"originY":200,"width":800,"height":600}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.prefix").value("2001:db8:b317:a000::/64"))
            .andExpect(jsonPath("$.ratePps").value(256))

        mvc
            .perform(get("/pinger/paint"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.originX").value(100))
            .andExpect(jsonPath("$.width").value(800))
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
