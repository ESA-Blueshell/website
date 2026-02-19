package net.blueshell.api.system.frontend.membership

import net.blueshell.api.domain.contribution.persistence.repository.ContributionPeriodRepository
import net.blueshell.api.factory.contribution.persistence.ContributionFactory
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class MembershipPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var contributionFactory: ContributionFactory

    @Autowired
    private lateinit var contributionPeriodRepository: ContributionPeriodRepository

    @Test
    fun `membership page displays contribution fees`() {
        val period = contributionFactory.createPeriod().apply {
            fullYearFee = 123.45
            halfYearFee = 67.89
            alumniFee = 10.11
        }
        contributionPeriodRepository.saveAndFlush(period)

        withPage { page ->
            page.navigate("$frontendUrl/membership")
            page.waitForURL("**/membership")

            waitFor(
                timeoutMs = 10_000,
                onTimeoutMessage = { "Expected membership page to render contribution fee details" }
            ) {
                page.locator("body").innerText().contains("membership fees for the academic year")
            }

            val bodyText = page.locator("body").innerText().replace('\u00A0', ' ')
            assertThat(bodyText).contains("full year membership")
            assertThat(bodyText).contains("half-year membership")
            assertThat(bodyText).contains("Alumni membership")
            assertThat(bodyText).containsPattern("€\\s*123[,\\.]45")
            assertThat(bodyText).containsPattern("€\\s*67[,\\.]89")
            assertThat(bodyText).containsPattern("€\\s*10[,\\.]11")
        }
    }
}
