package net.blueshell.api.user.api

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.domain.sealing.Sealer
import net.blueshell.api.user.domain.sealing.SealingUnavailable
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** With the key away, an address is refused rather than stored in plaintext. */
@SpringBootTest
class SealingUnavailableIT : UserTestSupport() {
    @MockitoBean
    private lateinit var sealer: Sealer

    @Test
    fun `saving an address is refused with a 503 when the key cannot be reached, and nothing is written`() {
        whenever(sealer.seal(any(), any())).thenThrow(SealingUnavailable())
        val ann = createUserWithRole(Role.MEMBER)

        mvc
            .perform(
                post("/addresses")
                    .with(signedIn(ann))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"userId":${ann.id},"country":"NL","city":"Enschede",""" +
                            """"street":"Noorderhagen","houseNumber":"14","zipCode":"7511EL"}""",
                    ),
            ).andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("SealingUnavailable"))
        org.assertj.core.api.Assertions
            .assertThat(refreshUser(ann).address)
            .isNull()
    }
}
