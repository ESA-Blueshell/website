package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.contact.api.ContactListAdapter
import net.blueshell.api.contact.api.ContactListMember
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** A system or adapter that cannot file, rename or make folders keeps the defaults, which refuse. */
class TargetStrategyDefaultsTest {
    private val strategy =
        object : TargetStrategy {
            override val descriptor = TargetDescriptor(TargetSystem.GOOGLE_CALENDAR, CohortKind.GROUP)

            override fun members(target: ExternalTarget) = emptyList<ExternalMember>()

            override fun add(
                target: ExternalTarget,
                externalUserId: String,
            ) = Unit

            override fun remove(
                target: ExternalTarget,
                externalUserId: String,
            ) = Unit

            override fun create(
                label: String,
                folder: String?,
            ) = handle(label)

            override fun delete(target: ExternalTarget) = Unit
        }

    private val adapter =
        object : ContactListAdapter {
            override val system = TargetSystem.GOOGLE_CALENDAR

            override fun createList(
                name: String,
                folderName: String?,
            ) = 1L

            override fun addToList(
                externalUserId: Long,
                externalListId: Long,
            ) = Unit

            override fun removeFromList(
                externalUserId: Long,
                externalListId: Long,
            ) = Unit

            override fun deleteList(externalListId: Long) = Unit

            override fun listMembers(externalListId: Long) = emptyList<ContactListMember>()
        }

    @Test
    fun `a strategy without folders or renames refuses them`() {
        val group = strategy.handle("board")

        assertThatThrownBy { strategy.move(group, "Committees") }.isInstanceOf(UnsupportedOperationException::class.java)
        assertThatThrownBy { strategy.rename(group, "Board") }.isInstanceOf(UnsupportedOperationException::class.java)
        assertThatThrownBy { strategy.createFolder("Committees") }.isInstanceOf(UnsupportedOperationException::class.java)
        refusedByDefaultImpls(TargetStrategy::class.java, "rename", strategy, group, "Board")
    }

    @Test
    fun `an adapter without folders or renames refuses them`() {
        assertThatThrownBy { adapter.moveList(1L, 2L) }.isInstanceOf(UnsupportedOperationException::class.java)
        assertThatThrownBy { adapter.renameList(1L, "Board") }.isInstanceOf(UnsupportedOperationException::class.java)
        assertThatThrownBy { adapter.createFolder("Committees") }.isInstanceOf(UnsupportedOperationException::class.java)
        refusedByDefaultImpls(ContactListAdapter::class.java, "renameList", adapter, 1L, "Board")
    }

    // Kotlin also compiles each default into a DefaultImpls copy for Java callers, which the
    // coverage report measures too; Kotlin callers never reach it.
    private fun refusedByDefaultImpls(
        owner: Class<*>,
        name: String,
        vararg args: Any,
    ) {
        val method = Class.forName(owner.name + "\$DefaultImpls").methods.single { it.name == name }

        assertThatThrownBy { method.invoke(null, *args) }.hasCauseInstanceOf(UnsupportedOperationException::class.java)
    }
}
