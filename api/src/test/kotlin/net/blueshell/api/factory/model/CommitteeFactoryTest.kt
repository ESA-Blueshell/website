package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class CommitteeFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable committee`() {
        val committee = committeeFactory.createBasic()
        val saved = persist(committee)
        assertPersisted(net.blueshell.api.model.committee.Committee::class.java, saved.id)
    }
}
