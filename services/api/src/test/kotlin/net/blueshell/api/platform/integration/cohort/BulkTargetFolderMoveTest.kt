package net.blueshell.api.platform.integration.cohort

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.platform.integration.cohort.application.TargetCatalog
import net.blueshell.api.platform.integration.cohort.application.TargetStrategies
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.port.out.ExternalTarget
import net.blueshell.api.platform.integration.cohort.port.out.TargetCapability
import net.blueshell.api.platform.integration.cohort.port.out.TargetDescriptor
import net.blueshell.api.platform.integration.cohort.port.out.TargetStrategy
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Moving a set of targets at once.
 *
 * The selection is checked whole before anything is sent, because an external system has no
 * transaction to undo a half-applied move. Past that point failures are individual and are
 * reported as such rather than being folded into one verdict for the batch.
 */
class BulkTargetFolderMoveTest {

    private val strategy = mockk<TargetStrategy>(relaxed = true)
    private val cohorts = mockk<CohortRepository>(relaxed = true) {
        every { findAllBySystem(any()) } returns emptyList()
    }

    private fun target(id: String, label: String) = ExternalTarget(
        system = TargetSystem.BREVO,
        externalId = id,
        kind = CohortKind.LIST,
        label = label,
        folderLabel = "Unfiled",
    )

    private fun catalog(capabilities: Set<TargetCapability> = setOf(TargetCapability.MOVE)): TargetCatalog {
        every { strategy.system } returns TargetSystem.BREVO
        every { strategy.descriptor } returns TargetDescriptor(
            system = TargetSystem.BREVO,
            kind = CohortKind.LIST,
            systemLabel = "Brevo",
            targetLabel = "List",
            idLabel = "List id",
            folderLabel = "Folder",
            capabilities = capabilities,
        )
        return TargetCatalog(TargetStrategies(listOf(strategy)), cohorts)
    }

    @Test
    fun `every selected target is filed under the destination`() {
        val web = target("7", "Web Cmte")
        val board = target("9", "Board")
        every { strategy.folders() } returns listOf("Committees")
        every { strategy.resolve("7") } returns web
        every { strategy.resolve("9") } returns board
        every { strategy.move(web, "Committees") } returns web.copy(folderLabel = "Committees")
        every { strategy.move(board, "Committees") } returns board.copy(folderLabel = "Committees")

        val result = catalog().moveAll(TargetSystem.BREVO, listOf("7", "9"), "Committees")

        assertThat(result.moved).extracting<String> { it.externalId }.containsExactly("7", "9")
        assertThat(result.moved).allMatch { it.folderLabel == "Committees" }
        assertThat(result.failed).isEmpty()
    }

    @Test
    fun `an unknown folder refuses the selection whole and sends nothing`() {
        every { strategy.folders() } returns listOf("Committees")
        every { strategy.resolve(any()) } returns target("7", "Web Cmte")

        assertThatThrownBy { catalog().moveAll(TargetSystem.BREVO, listOf("7"), "Comittees") }
            .isInstanceOf(BulkSelectionRejected::class.java)
            .extracting { (it as BulkSelectionRejected).violations }
            .satisfies({ violations ->
                assertThat(violations).singleElement()
                    .satisfies({ violation ->
                        assertThat(violation.code).isEqualTo(BulkSelectionRejected.UNKNOWN_FOLDER)
                        assertThat(violation.field).isEqualTo("folder")
                        assertThat(violation.refs).containsExactly("Comittees")
                    })
            })

        verify(exactly = 0) { strategy.move(any(), any()) }
    }

    @Test
    fun `one target that has gone refuses the whole selection, so the rest are not moved either`() {
        every { strategy.folders() } returns listOf("Committees")
        every { strategy.resolve("7") } returns target("7", "Web Cmte")
        every { strategy.resolve("404") } returns null

        assertThatThrownBy { catalog().moveAll(TargetSystem.BREVO, listOf("7", "404"), "Committees") }
            .isInstanceOf(BulkSelectionRejected::class.java)
            .satisfies({ thrown ->
                val violation = (thrown as BulkSelectionRejected).violations.single()
                assertThat(violation.code).isEqualTo(BulkSelectionRejected.UNKNOWN_TARGETS)
                assertThat(violation.field).isEqualTo("externalIds")
                // The id is a string because an external system names its own rows.
                assertThat(violation.refs).containsExactly("404")
                assertThat(violation.values).isEmpty()
            })

        // The operator chose a set; acting on part of it would leave them believing the
        // whole set moved.
        verify(exactly = 0) { strategy.move(any(), any()) }
    }

    @Test
    fun `a system refusing one move does not undo the moves already made`() {
        val web = target("7", "Web Cmte")
        val board = target("9", "Board")
        every { strategy.folders() } returns listOf("Committees")
        every { strategy.resolve("7") } returns web
        every { strategy.resolve("9") } returns board
        every { strategy.move(web, "Committees") } returns web.copy(folderLabel = "Committees")
        every { strategy.move(board, "Committees") } throws IllegalStateException("Brevo said no")

        val result = catalog().moveAll(TargetSystem.BREVO, listOf("7", "9"), "Committees")

        assertThat(result.moved).singleElement().satisfies({ assertThat(it.externalId).isEqualTo("7") })
        assertThat(result.failed).singleElement().satisfies({ failure ->
            assertThat(failure.externalId).isEqualTo("9")
            assertThat(failure.label).isEqualTo("Board")
            assertThat(failure.message).isEqualTo("Brevo said no")
        })
    }

    @Test
    fun `the destination is matched however it is capitalised`() {
        val web = target("7", "Web Cmte")
        every { strategy.folders() } returns listOf("Committees")
        every { strategy.resolve("7") } returns web
        every { strategy.move(web, "Committees") } returns web.copy(folderLabel = "Committees")

        catalog().moveAll(TargetSystem.BREVO, listOf("7"), "committees")

        // The folder the system actually has, not the spelling that was typed.
        verify { strategy.move(web, "Committees") }
    }

    @Test
    fun `the same target named twice is moved once`() {
        val web = target("7", "Web Cmte")
        every { strategy.folders() } returns listOf("Committees")
        every { strategy.resolve("7") } returns web
        every { strategy.move(web, "Committees") } returns web.copy(folderLabel = "Committees")

        val result = catalog().moveAll(TargetSystem.BREVO, listOf("7", "7"), "Committees")

        assertThat(result.moved).hasSize(1)
        verify(exactly = 1) { strategy.move(any(), any()) }
    }

    @Test
    fun `a system that cannot move says so instead of being asked`() {
        assertThatThrownBy {
            catalog(capabilities = setOf(TargetCapability.CATALOG))
                .moveAll(TargetSystem.BREVO, listOf("7"), "Committees")
        }.isInstanceOf(IllegalArgumentException::class.java)

        verify(exactly = 0) { strategy.move(any(), any()) }
    }
}
