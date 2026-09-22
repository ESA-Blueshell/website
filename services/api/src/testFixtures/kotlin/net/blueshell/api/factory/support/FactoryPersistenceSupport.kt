package net.blueshell.api.factory.support

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class FactoryPersistenceSupport(
    private val entityManager: EntityManager,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /** The rows a seed would otherwise duplicate: a second run reuses what the first one wrote. */
    fun <T> query(
        jpql: String,
        type: Class<T>,
    ): List<T> =
        transactionTemplate.execute {
            entityManager.createQuery(jpql, type).resultList
        }!!

    fun <T> persist(entity: T): T =
        transactionTemplate.execute {
            val saved = entityManager.merge(entity)
            entityManager.flush()
            entityManager.refresh(saved)
            saved
        }!!
}
