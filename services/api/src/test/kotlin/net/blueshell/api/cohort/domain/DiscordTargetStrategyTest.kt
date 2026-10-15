package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.discord.api.RoleHolder
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DiscordTargetStrategyTest {
    private val roles: DiscordRoleKeeper = mock()
    private val users: UserService = mock()
    private val strategy = DiscordTargetStrategy(roles, users)
    private val board = KeptRole("902", "Board", true)
    private val sitecie = KeptRole("901", "Sitecie", true)

    private fun target(role: KeptRole) = ExternalTarget(TargetSystem.DISCORD, role.id, TargetKind.ROLE, role.name, path = listOf("Discord"))

    @Test
    fun `lists and finds the roles the site could keep, and none without a bot`() {
        whenever(roles.available()).thenReturn(true)
        whenever(roles.roles()).thenReturn(listOf(board, sitecie))
        whenever(roles.role("902")).thenReturn(board)

        assertThat(strategy.descriptor).isEqualTo(TargetDescriptor(TargetSystem.DISCORD, TargetKind.ROLE))
        assertThat(strategy.available()).isTrue()
        assertThat(strategy.catalog(" site ")).containsExactly(target(sitecie))
        assertThat(strategy.catalog("902")).containsExactly(target(board))
        assertThat(strategy.catalog(null)).hasSize(2)
        assertThat(strategy.resolve("902")).isEqualTo(target(board))

        whenever(roles.available()).thenReturn(false)
        assertThat(strategy.catalog(null)).isEmpty()
        assertThat(strategy.resolve("902")).isNull()
        verify(roles, times(3)).roles()
    }

    @Test
    fun `a member is on a role through the Discord account they linked, and nobody else is reachable`() {
        val linked = Entities.user(id = 1).apply { discordId = "d1" }
        val blank = Entities.user(id = 2).apply { discordId = " " }
        val none = Entities.user(id = 3).apply { discordId = null }
        whenever(users.findAllByIds(setOf(1L, 2L, 3L))).thenReturn(listOf(linked, blank, none))
        whenever(users.findAllByDiscordIds(setOf("d1", "d9"))).thenReturn(listOf(linked))
        whenever(roles.holders("902")).thenReturn(listOf(RoleHolder("d1", "Ann")))

        assertThat(strategy.memberIds(setOf(1L, 2L, 3L))).isEqualTo(mapOf(1L to "d1"))
        assertThat(strategy.ownersOf(setOf("d1", "d9"))).isEqualTo(mapOf("d1" to 1L))
        assertThat(strategy.makesMemberIds).isFalse()
        strategy.makeMemberId(3L)
        assertThat(strategy.members(target(board))).containsExactly(ExternalMember("d1", "Ann"))
    }

    @Test
    fun `adds, removes, makes, renames and deletes roles through the keeper, and has no folders`() {
        whenever(roles.create("Sitecie")).thenReturn(sitecie)
        whenever(roles.rename("902", "Bestuur")).thenReturn(board.copy(name = "Bestuur"))

        strategy.add(target(board), "d1")
        strategy.remove(target(board), "d2")
        assertThat(strategy.create("Sitecie", "Committees")).isEqualTo(target(sitecie))
        assertThat(strategy.rename(target(board), "Bestuur").label).isEqualTo("Bestuur")
        strategy.delete(target(board))

        verify(roles).add("902", "d1")
        verify(roles).remove("902", "d2")
        verify(roles).delete("902")
        assertThatThrownBy { strategy.createFolder("Committees") }.isInstanceOf(UnsupportedOperationException::class.java)
    }
}
