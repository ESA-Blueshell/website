package net.blueshell.api.factory.model

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.membership.model.Membership
import org.junit.jupiter.api.Test

class MembershipFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable membership`() {
        val user = persistUser()
        val membership = membershipFactory.createBasic(user)
        membership.user = user

        val saved = persist(membership)
        assertPersisted(Membership::class.java, saved.id)
    }

    @Test
    fun `creates persistable active membership`() {
        val user = persistUser()
        val membership = membershipFactory.createActive(user)
        membership.user = user

        val saved = persist(membership)
        assertPersisted(Membership::class.java, saved.id)
    }

    @Test
    fun `creates persistable expired membership`() {
        val user = persistUser()
        val membership = membershipFactory.createExpired(user)
        membership.user = user

        val saved = persist(membership)
        assertPersisted(Membership::class.java, saved.id)
    }
}
