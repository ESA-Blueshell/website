package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.domain.IncassoFile
import net.blueshell.api.contribution.domain.IncassoFiles
import net.blueshell.api.contribution.domain.IncassoRunView
import net.blueshell.api.contribution.domain.IncassoRuns
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class IncassoRunControllerTest {
    private val runs: IncassoRuns = mock()
    private val files: IncassoFiles = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val controller = IncassoRunController(runs, files, currentUser)

    private val view = IncassoRunView(11, 4, LocalDate.of(2026, 11, 1), "Contributie", emptyList(), 0.0, Instant.EPOCH, null, 1)

    @Test
    fun `hands the plan, the start and the run through, with who started it`() {
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(id = 9, roles = emptySet(), addressId = null))
        whenever(runs.plan(4)).thenReturn(emptyList())
        whenever(
            runs.start(4, listOf(1L), mapOf(1L to BulkFeeType.HALF_YEAR_FEE), LocalDate.of(2026, 11, 1), "Contributie", 9),
        ).thenReturn(view)
        whenever(runs.find(11)).thenReturn(view)

        assertThat(controller.planIncasso(4)).isEmpty()
        val request =
            StartIncassoRunRequest(
                userIds = listOf(1L),
                feeTypeOverrides = mapOf(1L to BulkFeeType.HALF_YEAR_FEE),
                collectionDate = LocalDate.of(2026, 11, 1),
                statementText = "Contributie",
            )
        assertThat(controller.startIncassoRun(4, request)).isEqualTo(view)
        assertThat(controller.findIncassoRun(11)).isEqualTo(view)
        assertThat(StartIncassoRunRequest(collectionDate = null).statementText).isEmpty()
        assertThat(net.blueshell.api.contribution.persistence.IncassoRun::class.java.getDeclaredConstructor().newInstance()).isNotNull
    }

    @Test
    fun `answers ING's file as an attachment kept nowhere, and marks the run submitted by who said so`() {
        whenever(files.file(11, 2, 9)).thenReturn(IncassoFile("incassobatch-2026-11-01-2-of-2.xlsx", byteArrayOf(1, 2)))
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(id = 9, roles = emptySet(), addressId = null))
        whenever(runs.markSubmitted(11, 9)).thenReturn(view)

        val answer = controller.downloadIncassoFile(11, 2)

        assertThat(answer.body).containsExactly(1, 2)
        assertThat(answer.headers.getFirst("Content-Disposition")).contains("incassobatch-2026-11-01-2-of-2.xlsx")
        assertThat(answer.headers.cacheControl).isEqualTo("no-store")
        assertThat(controller.markIncassoRunSubmitted(11)).isEqualTo(view)
    }
}
