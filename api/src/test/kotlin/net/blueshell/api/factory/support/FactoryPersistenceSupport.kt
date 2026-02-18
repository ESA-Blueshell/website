package net.blueshell.api.factory.support

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class FactoryPersistenceSupport(
    private val entityManager: EntityManager,
    transactionManager: PlatformTransactionManager
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun <T> persist(entity: T): T {
        return transactionTemplate.execute {
            val saved = entityManager.merge(entity)
            entityManager.flush()
            entityManager.refresh(saved)
            saved
        }!!
    }
}
