package net.blueshell.api.factory.model

import net.blueshell.api.model.committee.Committee
import org.junit.jupiter.api.Test

class CommitteeFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable committee`() {
        val committee = committeeFactory.createBasic()
        val saved = persist(committee)
        assertPersisted(Committee::class.java, saved.id)
    }
}
