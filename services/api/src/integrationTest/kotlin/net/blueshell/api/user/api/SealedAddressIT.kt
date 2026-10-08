package net.blueshell.api.user.api

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** A member's address is sealed to them: no plaintext in the table, and copied to somebody else it does not open. */
@SpringBootTest
class SealedAddressIT : UserTestSupport() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var sealedAddresses: SealedAddresses

    private fun saveAddress(userId: Long): Long {
        val created =
            mvc
                .perform(
                    post("/addresses")
                        .with(signedIn(userRepository.findById(userId).orElseThrow()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"userId":$userId,"country":"NL","city":"Enschede",""" +
                                """"street":"Noorderhagen","houseNumber":"14","zipCode":"7511EL"}""",
                        ),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.city").value("Enschede"))
                .andExpect(jsonPath("$.opened").value(true))
                .andReturn()
                .response.contentAsString
        return Regex("\"id\":(\\d+)").find(created)!!.groupValues[1].toLong()
    }

    @Test
    fun `an address is stored sealed, with no plaintext a database copy could read`() {
        val ann = createUserWithRole(Role.MEMBER)
        val id = saveAddress(ann.id!!)

        val row = jdbc.queryForMap("SELECT country, city, street, house_number, zip_code, sealed_address FROM addresses WHERE id = ?", id)
        assertThat(listOf(row["country"], row["city"], row["street"], row["house_number"], row["zip_code"])).containsOnlyNulls()
        assertThat(row["sealed_address"].toString()).doesNotContain("Enschede", "Noorderhagen", "7511EL")
        mvc
            .perform(get("/addresses/{id}", id).with(signedIn(ann)))
            .andExpect(jsonPath("$.street").value("Noorderhagen"))
    }

    @Test
    fun `a sealed address copied onto another member's row does not open there`() {
        val ann = createUserWithRole(Role.MEMBER)
        val bob = createUserWithRole(Role.MEMBER)
        val annsAddress = saveAddress(ann.id!!)
        val bobsAddress = saveAddress(bob.id!!)

        jdbc.update(
            "UPDATE addresses SET sealed_address = (SELECT s FROM (SELECT sealed_address AS s FROM addresses WHERE id = ?) t) WHERE id = ?",
            annsAddress,
            bobsAddress,
        )

        mvc
            .perform(get("/addresses/{id}", bobsAddress).with(signedIn(bob)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.opened").value(false))
            .andExpect(jsonPath("$.street").doesNotExist())
    }

    @Test
    fun `the job seals every address left in plaintext, soft-deleted ones too, and can run again`() {
        val ann = createUserWithRole(Role.MEMBER)
        val id = saveAddress(ann.id!!)
        jdbc.update(
            "UPDATE addresses SET sealed_address = NULL, city = 'Hengelo', street = 'Markt', country = 'NL', " +
                "house_number = '1', zip_code = '7551' WHERE id = ?",
            id,
        )
        jdbc.update("UPDATE addresses SET deleted_at = NOW() WHERE id = ?", id)

        assertThat(sealedAddresses.sealEvery()).isGreaterThanOrEqualTo(1)
        assertThat(sealedAddresses.sealEvery()).isZero()

        val row = jdbc.queryForMap("SELECT city, sealed_address FROM addresses WHERE id = ?", id)
        assertThat(row["city"]).isNull()
        assertThat(row["sealed_address"].toString()).doesNotContain("Hengelo")
    }

    @Test
    fun `no list of people opens an address`() {
        val board = createUserWithRole(Role.ADMIN)
        saveAddress(createUserWithRole(Role.MEMBER).id!!)

        mvc
            .perform(get("/addresses").with(signedIn(board)))
            .andExpect(jsonPath("$[0].opened").value(false))
            .andExpect(jsonPath("$[*].city").isEmpty)
    }
}
