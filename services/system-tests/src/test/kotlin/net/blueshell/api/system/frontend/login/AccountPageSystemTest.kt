package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.LoginDomainHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class AccountPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Test
    fun `member can update editable account fields`() {
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        member.replaceMemberProfile(userFactory.buildMemberProfile(member))
        userRepository.saveAndFlush(member)
        userFactory.createMembership(member)

        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val updatedDiscord = "account$suffix"
        val updatedPhone = "+3161${suffix.takeLast(7)}"

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/account")
            page.waitForURL("**/account")

            page.getByLabel("Discord*", Page.GetByLabelOptions().setExact(true)).fill(updatedDiscord)
            page.getByLabel("Phone Number*", Page.GetByLabelOptions().setExact(true)).fill(updatedPhone)
            LoginDomainHelper.clickAccountSubmit(page)

            waitFor(
                timeoutMs = 10_000,
                onTimeoutMessage = {
                    "Expected account update to persist discord and phone for user ${member.username}"
                }
            ) {
                val refreshed = userRepository.findById(member.id!!).orElseThrow()
                refreshed.discord == updatedDiscord && refreshed.phoneNumber == updatedPhone
            }
        }

        val refreshed = userRepository.findById(member.id!!).orElseThrow()
        assertThat(refreshed.discord).isEqualTo(updatedDiscord)
        assertThat(refreshed.phoneNumber).isEqualTo(updatedPhone)
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
