package net.blueshell.api.factory.model

import net.blueshell.api.auth.persistence.RecoveryToken
import org.junit.jupiter.api.Test

class RecoveryTokenFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable recovery token`() {
        val user = persistUser()
        val token = recoveryTokenFactory.createBasic()
        token.user = user

        val saved = persist(token)
        assertPersisted(RecoveryToken::class.java, saved.id)
    }
}
