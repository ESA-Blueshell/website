package net.blueshell.api.sponsor.web

import net.blueshell.api.sponsor.persistence.SponsorRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class SponsorControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var sponsorRepository: SponsorRepository

    private fun createPayload(name: String = "Sponsor ${System.currentTimeMillis()}"): String =
        """{"name":"$name","description":"Sponsor description"}"""

    private fun updatePayload(version: Long, name: String = "Updated Sponsor ${System.currentTimeMillis()}"): String =
        """{"name":"$name","description":"Updated sponsor description","version":$version}"""

    @Nested
    inner class FindSponsors {

        @Test
        fun `lists sponsors`() {
            val board = createUserWithRole(Role.BOARD)
            createSponsorFixture()

            mvc.perform(
                get("/sponsors")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$[0].id").isNumber)
        }
    }

    @Nested
    inner class CreateSponsor {

        @Test
        fun `creates sponsor`() {
            val board = createUserWithRole(Role.BOARD)
            val sponsorName = "Sponsor ${System.currentTimeMillis()}"

            mvc.perform(
                post("/sponsors")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(sponsorName))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNumber)
                .andExpect(jsonPath("$.name").value(sponsorName))
                .andExpect(jsonPath("$.description").value("Sponsor description"))
        }

        @Test
        fun `returns bad request for invalid payload`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/sponsors")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"","description":"Sponsor description"}""")
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class UpdateSponsor {

        @Test
        fun `updates sponsor`() {
            val board = createUserWithRole(Role.BOARD)
            val sponsor = createSponsorFixture()
            val sponsorId = sponsor.id!!
            val updatedName = "Updated Sponsor ${System.currentTimeMillis()}"

            mvc.perform(
                put("/sponsors/{id}", sponsorId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(sponsor.version, updatedName))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(sponsorId))
                .andExpect(jsonPath("$.name").value(updatedName))
                .andExpect(jsonPath("$.description").value("Updated sponsor description"))

            val updated = sponsorRepository.findById(sponsorId).orElseThrow()
            assertThat(updated.name).isEqualTo(updatedName)
            assertThat(updated.description).isEqualTo("Updated sponsor description")
        }

        @Test
        fun `returns not found when sponsor does not exist`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                put("/sponsors/{id}", 999999L)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(0))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class FindSponsorById {

        @Test
        fun `finds sponsor by id`() {
            val board = createUserWithRole(Role.BOARD)
            val sponsor = createSponsorFixture()

            mvc.perform(
                get("/sponsors/{id}", sponsor.id)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(sponsor.id))
                .andExpect(jsonPath("$.name").value(sponsor.name))
                .andExpect(jsonPath("$.description").value(sponsor.description))
        }

        @Test
        fun `returns not found when sponsor does not exist`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/sponsors/{id}", 999999L)
                    .with(bearer(board))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class DeleteSponsorById {

        @Test
        fun `deletes sponsor by id`() {
            val board = createUserWithRole(Role.BOARD)
            val sponsor = createSponsorFixture()

            mvc.perform(
                delete("/sponsors/{id}", sponsor.id)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)

            assertThat(sponsorRepository.existsById(sponsor.id!!)).isFalse()
        }

        @Test
        fun `returns not found when deleting missing sponsor`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                delete("/sponsors/{id}", 999999L)
                    .with(bearer(board))
            )
                .andExpect(status().isNotFound)
        }
    }
}
