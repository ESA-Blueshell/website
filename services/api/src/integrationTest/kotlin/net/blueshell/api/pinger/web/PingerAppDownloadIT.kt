package net.blueshell.api.pinger.web

import net.blueshell.api.pinger.api.ReleaseAsset
import net.blueshell.api.pinger.api.ReleaseAssets
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The member download gate. A stubbed [ReleaseAssets] stands in for the GitHub releases API, so the
 * test asserts the gate and the OS resolution without reaching the network.
 */
@SpringBootTest
@Import(PingerAppDownloadIT.StubReleases::class)
class PingerAppDownloadIT : UserTestSupport() {
    @Test
    fun `a signed-in member is redirected to the installer for their os`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc
            .perform(get("/pinger/app/download").param("os", "macos").with(signedIn(member)))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "https://releases.test/Blueshell-Pinger.dmg"))
    }

    @Test
    fun `an os with no installer on the release is not found`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc
            .perform(get("/pinger/app/download").param("os", "solaris").with(signedIn(member)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a guest may not download`() {
        val guest = createUserWithRole(Role.GUEST)

        mvc
            .perform(get("/pinger/app/download").param("os", "macos").with(signedIn(guest)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `a signed-out visitor may not download`() {
        mvc
            .perform(get("/pinger/app/download").param("os", "macos"))
            .andExpect(status().isUnauthorized)
    }

    @TestConfiguration
    class StubReleases {
        @Bean
        @Primary
        fun stubReleaseAssets(): ReleaseAssets =
            object : ReleaseAssets {
                override fun latest(): List<ReleaseAsset> =
                    listOf(ReleaseAsset("Blueshell-Pinger.dmg", "https://releases.test/Blueshell-Pinger.dmg"))
            }
    }
}
