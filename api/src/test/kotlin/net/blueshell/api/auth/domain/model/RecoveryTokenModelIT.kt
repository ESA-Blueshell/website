package net.blueshell.api.auth.persistence

import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.domain.user.persistence.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RecoveryTokenModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val user = persist(userFactory.createBasic())
            val token = recoveryTokenFactory.createBasic()
            token.user = user
            token.type = ResetType.PASSWORD_RESET
            token.selector = unique("selector").replace("-", "").take(32)
            token.verifierHash = unique("hash")
            token.expiresAt = timestamp().plusSeconds(3600)
            token.consumedAt = timestamp()

            val found = persistAndReload(token, RecoveryToken::class.java) { it.id }

            assertEquals(token.type, found.type)
            assertEquals(token.selector, found.selector)
            assertEquals(token.verifierHash, found.verifierHash)
            assertEquals(token.expiresAt, found.expiresAt)
            assertEquals(token.consumedAt, found.consumedAt)
        }

        @Test
        fun `persists user relation when setting entity`() {
            val user = persist(userFactory.createBasic())
            val token = recoveryTokenFactory.createBasic()
            token.user = user
            token.type = ResetType.PASSWORD_RESET
            token.selector = unique("selector").replace("-", "").take(32)
            token.verifierHash = unique("hash")
            token.expiresAt = timestamp().plusSeconds(3600)

            val found = persistAndReload(token, RecoveryToken::class.java) { it.id }

            assertEquals(user.id, found.user.id)
        }

        @Test
        fun `persists user relation when setting id`() {
            val user = persist(userFactory.createBasic())
            val token = recoveryTokenFactory.createBasic()
            token.user = entityManager.getReference(User::class.java, user.id)
            token.type = ResetType.PASSWORD_RESET
            token.selector = unique("selector").replace("-", "").take(32)
            token.verifierHash = unique("hash")
            token.expiresAt = timestamp().plusSeconds(3600)

            val found = persistAndReload(token, RecoveryToken::class.java) { it.id }

            assertEquals(user.id, found.user.id)
        }
    }
}
