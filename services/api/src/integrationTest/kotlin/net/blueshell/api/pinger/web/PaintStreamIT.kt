package net.blueshell.api.pinger.web

import net.blueshell.api.pinger.persistence.PingerPlacement
import net.blueshell.api.pinger.persistence.PingerPlacementRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * The paint stream and the motion endpoint. A stream stays open in the shared [PaintStream] until it
 * completes, so each test completes the one it opened. The placements are saved straight to the
 * repository, since adding one through the api needs a stored image.
 */
@SpringBootTest
class PaintStreamIT : UserTestSupport() {
    @Autowired
    private lateinit var placements: PingerPlacementRepository

    private val opened = mutableListOf<MvcResult>()

    @AfterEach
    fun closeStreams() {
        opened.forEach { it.request.asyncContext?.complete() }
    }

    private fun placement(): PingerPlacement =
        placements.save(
            PingerPlacement(
                imagePath = "pinger-paint/stream.webp",
                originX = 100,
                originY = 200,
                width = 400,
                height = 300,
                ordinal = 9,
                motionEpoch = Instant.now(),
            ),
        )

    private fun openStream(): MvcResult =
        mvc
            .perform(get("/pinger/paint/stream"))
            .andExpect(status().isOk)
            .andExpect(request().asyncStarted())
            .andReturn()
            .also { opened += it }

    @Test
    fun `an anonymous stream is sent the paint job on connect`() {
        val saved = placement()

        val stream = openStream()

        val body = stream.response.contentAsString
        assertThat(body).startsWith("data:")
        assertThat(body).contains("\"serverTime\"")
        assertThat(body).contains("\"id\":${saved.id}")
        assertThat(body).contains("\"motion\":{\"mode\":\"static\",\"vx\":0.0,\"vy\":0.0}")
    }

    @Test
    fun `a stream is sent the paint job again when an admin moves a placement`() {
        val saved = placement()
        val admin = createUserWithRole(Role.ADMIN)
        val stream = openStream()

        mvc
            .perform(
                put("/pinger/paint/placements/${saved.id}")
                    .with(signedIn(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"originX":1234,"originY":200,"width":400,"height":300}"""),
            ).andExpect(status().isOk)

        assertThat(stream.response.contentAsString).contains("\"originX\":1234")
    }

    @Test
    fun `a stream is sent the paint job again when an admin sets a placement bouncing`() {
        val saved = placement()
        val admin = createUserWithRole(Role.ADMIN)
        val stream = openStream()

        mvc
            .perform(
                put("/pinger/placements/${saved.id}/motion")
                    .with(signedIn(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"mode":"bounce","vx":150.5,"vy":-90}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.motion.mode").value("bounce"))
            .andExpect(jsonPath("$.motion.vx").value(150.5))
            .andExpect(jsonPath("$.motion.vy").value(-90.0))
            .andExpect(jsonPath("$.motionEpoch").exists())

        assertThat(stream.response.contentAsString).contains("\"motion\":{\"mode\":\"bounce\",\"vx\":150.5,\"vy\":-90.0}")
        assertThat(placements.findById(saved.id!!).get().motionVx).isEqualTo(150.5)
    }

    @Test
    fun `only an admin sets a placement's motion`() {
        val saved = placement()
        val body = """{"mode":"bounce","vx":10,"vy":10}"""

        mvc
            .perform(
                put("/pinger/placements/${saved.id}/motion")
                    .with(signedIn(createUserWithRole(Role.MEMBER)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isForbidden)
        mvc
            .perform(put("/pinger/placements/${saved.id}/motion").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a speed over 2000 px per second or an unknown mode is refused`() {
        val saved = placement()
        val admin = createUserWithRole(Role.ADMIN)

        listOf(
            """{"mode":"bounce","vx":2000.5,"vy":0}""",
            """{"mode":"bounce","vx":0,"vy":-2001}""",
            """{"mode":"spin","vx":0,"vy":0}""",
        ).forEach { body ->
            mvc
                .perform(
                    put("/pinger/placements/${saved.id}/motion")
                        .with(signedIn(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                ).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `setting motion on an unknown placement is not found`() {
        mvc
            .perform(
                put("/pinger/placements/999999/motion")
                    .with(signedIn(createUserWithRole(Role.ADMIN)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"mode":"static","vx":0,"vy":0}"""),
            ).andExpect(status().isNotFound)
    }
}
