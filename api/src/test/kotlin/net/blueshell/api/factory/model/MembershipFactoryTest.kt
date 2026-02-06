package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class MembershipFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable membership`() {
        val user = persistUser()
        val membership = membershipFactory.createBasic(user)
        membership.user = user

        val saved = persist(membership)
        assertPersisted(net.blueshell.api.model.Membership::class.java, saved.id)
    }
}
