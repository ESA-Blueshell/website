package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class CommitteeMemberFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable committee member`() {
        val committee = persistCommittee()
        val user = persistUser()

        val member = committeeMemberFactory.createBasic(user, committee)

        val saved = persist(member)
        assertPersisted(net.blueshell.api.model.committee.CommitteeMember::class.java, saved.id)
    }
}
