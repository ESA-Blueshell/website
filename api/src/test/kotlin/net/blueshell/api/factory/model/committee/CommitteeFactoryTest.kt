package net.blueshell.api.factory.model.committee

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.committee.domain.model.Committee
import org.junit.jupiter.api.Test

class CommitteeFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable committee`() {
        val committee = committeeFactory.createBasic()
        val saved = persist(committee)
        assertPersisted(Committee::class.java, saved.id)
    }
}
