package net.blueshell.api.integration.model

import net.blueshell.api.common.enums.ResetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RecoveryTokenModelIT : net.blueshell.api.integration.model.ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_columns_and_user_relation() {
            val user = persist(userFactory.createBasic())
            val token = recoveryTokenFactory.createBasic()
            token.user = user
            token.type = ResetType.PASSWORD_RESET
            token.selector = unique("selector").replace("-", "").take(32)
            token.verifierHash = unique("hash")
            token.expiresAt = timestamp().plusSeconds(3600)
            token.consumedAt = timestamp()

            val found = persistAndReload(token, RecoveryToken::class.java) { it.id }

            assertEquals(user.id, found.user.id)
            assertEquals(token.type, found.type)
            assertEquals(token.selector, found.selector)
            assertEquals(token.verifierHash, found.verifierHash)
            assertEquals(token.expiresAt, found.expiresAt)
            assertEquals(token.consumedAt, found.consumedAt)
        }
    }
}
