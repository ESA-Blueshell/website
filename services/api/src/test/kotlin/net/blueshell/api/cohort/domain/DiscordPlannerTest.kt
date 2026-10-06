package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.cohort.web.DiscordPlaceController
import net.blueshell.api.cohort.web.DiscordPlanAsk
import net.blueshell.api.cohort.web.DiscordPlanRequest
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DiscordPlannerTest {
    private val cohorts: CohortRepository = mock()
    private val targets: TargetRepository = mock()
    private val targetIds: CohortTargetIds = mock()
    private val registrar: CohortRegistrar = mock()
    private val roles: DiscordRoleKeeper = mock()
    private val channels: DiscordChannelKeeper = mock()
    private val planner = DiscordPlanner(cohorts, targets, targetIds, registrar, roles, channels, "Committees", "Esports", "Board")

    private val sitecie = Entities.cohort(id = 1, label = "Sitecie")
    private val lancie = Entities.cohort(id = 2, label = "LanCie")
    private val valorant = Entities.cohort(id = 3, type = CohortType.TEAM_PLAYERS, label = "Blueshell Valorant")
    private val kandi = Entities.cohort(id = 4, type = CohortType.KANDI, label = "Kandi")

    private fun given() {
        whenever(roles.available()).thenReturn(true)
        whenever(channels.available()).thenReturn(true)
        whenever(cohorts.findByDefinitionKey("COMMITTEE_MEMBERS:1")).thenReturn(sitecie)
        whenever(cohorts.findByDefinitionKey("COMMITTEE_MEMBERS:2")).thenReturn(lancie)
        whenever(cohorts.findByDefinitionKey("TEAM_PLAYERS:3")).thenReturn(valorant)
        whenever(cohorts.findByDefinitionKey("KANDI")).thenReturn(kandi)
        val lancieRole = Entities.target(id = 20, system = "DISCORD", cohortId = 2, externalId = "902")
        whenever(targets.findAllBySystem("DISCORD")).thenReturn(listOf(lancieRole))
        whenever(targets.findByCohortIdAndSystem(2, "DISCORD")).thenReturn(lancieRole)
        whenever(targetIds.find(lancieRole)).thenReturn("902")
        whenever(roles.roles()).thenReturn(listOf(KeptRole("901", "SiteCie", true), KeptRole("902", "LanCie", true)))
        // What an existing role has access to is carried along, so applying the row keeps it; a category is not.
        whenever(channels.openedTo("901")).thenReturn(
            listOf(
                KeptChannel("10", "sitecie", KeptChannelKind.TEXT, null),
                KeptChannel("12", "Committees", KeptChannelKind.CATEGORY, null),
            ),
        )
        whenever(channels.channels()).thenReturn(
            listOf(
                KeptChannel("10", "sitecie", KeptChannelKind.TEXT, "Committees"),
                KeptChannel("11", "sitecie", KeptChannelKind.VOICE, "Committees"),
                KeptChannel("12", "Committees", KeptChannelKind.CATEGORY, null),
            ),
        )
    }

    @Test
    fun `links what exists by its plain name, keeps a role already followed, and plans the rest as new`() {
        given()

        val plan =
            planner.plan(
                listOf(
                    PlanAsk("COMMITTEE_MEMBERS:1", "Site-Cie"),
                    PlanAsk("COMMITTEE_MEMBERS:2", "#lan-cie "),
                    PlanAsk("TEAM_PLAYERS:3", " "),
                    PlanAsk("KANDI", "kandi"),
                    PlanAsk("COMMITTEE_MEMBERS:9", "gone"),
                ),
            )

        assertThat(plan).containsExactly(
            DiscordPlanRow(
                "COMMITTEE_MEMBERS:1",
                "Sitecie",
                PlannedRole("901", "SiteCie", kept = false, opens = listOf("10")),
                PlannedChannel("10", "sitecie", "Committees"),
                "Committees",
            ),
            DiscordPlanRow(
                "COMMITTEE_MEMBERS:2",
                "LanCie",
                PlannedRole("902", "LanCie", kept = true),
                PlannedChannel(null, "lan-cie", "Committees"),
                "Committees",
            ),
            DiscordPlanRow("TEAM_PLAYERS:3", "Blueshell Valorant", PlannedRole(null, "Blueshell Valorant", kept = false), null, "Esports"),
            DiscordPlanRow("KANDI", "Kandi", PlannedRole(null, "Kandi", kept = false), PlannedChannel(null, "kandi", "Board"), "Board"),
        )
        assertThat(listOf(plan[1].role.kept, plan[0].channel?.channelId, plan[2].category)).containsExactly(true, "10", "Esports")
        // A key with no record yet asks the definitions to be registered first.
        verify(registrar).register()
    }

    @Test
    fun `refuses without a bot, and the controller hands the rows over`() {
        whenever(roles.available()).thenReturn(false)
        assertThatThrownBy { planner.plan(listOf(PlanAsk("KANDI", null))) }.isInstanceOf(TargetSystemUnavailable::class.java)

        given()
        val controller = DiscordPlaceController(mock(), mock(), planner, "Committees", "Esports", "Board")
        val rows = controller.planDiscord(DiscordPlanRequest(listOf(DiscordPlanAsk("KANDI"))))
        assertThat(rows.single().channel).isNull()
    }
}
