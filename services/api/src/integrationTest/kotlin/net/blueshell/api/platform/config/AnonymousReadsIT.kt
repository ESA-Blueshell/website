package net.blueshell.api.platform.config

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class AnonymousReadsIT : UserTestSupport() {
    @ParameterizedTest
    @ValueSource(strings = ["/discord/roles", "/discord/game-channels", "/discord/emojis", "/discord/channels", "/esports/teams/1/seasons"])
    fun `a visitor is asked to log in for a read only editors make`(path: String) {
        mvc.perform(get(path)).andExpect(status().isUnauthorized)
    }

    @ParameterizedTest
    @ValueSource(strings = ["/discord/roles", "/discord/game-channels", "/discord/emojis", "/discord/channels", "/esports/teams/1/seasons"])
    fun `a signed-in member reads it`(path: String) {
        val member = createUserWithRole(Role.MEMBER)

        val status = mvc.perform(get(path).with(signedIn(member))).andReturn().response.status

        assertThat(status).isNotIn(401, 403)
    }
}
