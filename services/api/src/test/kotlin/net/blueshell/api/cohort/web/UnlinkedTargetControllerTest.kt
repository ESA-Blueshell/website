package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.UnlinkedTarget
import net.blueshell.api.cohort.domain.UnlinkedTargets
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException

class UnlinkedTargetControllerTest {
    private val unlinked: UnlinkedTargets = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val controller = UnlinkedTargetController(unlinked, currentUser)

    @Test
    fun `lists the reader's own unlinked targets, and refuses nobody`() {
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(7, emptySet(), null))
        whenever(unlinked.of(7)).thenReturn(listOf(UnlinkedTarget(TargetSystem.DISCORD, "Sitecie")))

        assertThat(controller.listMyUnlinkedTargets()).containsExactly(UnlinkedTargetResponse(TargetSystem.DISCORD, "Sitecie"))

        whenever(currentUser.currentUser()).thenReturn(null)
        assertThatThrownBy { controller.listMyUnlinkedTargets() }.isInstanceOf(ResponseStatusException::class.java)
    }
}
