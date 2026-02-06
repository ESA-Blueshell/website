package net.blueshell.api.factory

import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component

/**
 * Registry of DTO factories by their target type.
 */
@Component
class DtoFactoryRegistry(private val factories: List<BaseDtoFactory<*>>) {

    private var byType: MutableMap<Class<*>, BaseDtoFactory<*>>? = null

    private fun index(): Map<Class<*>, BaseDtoFactory<*>> {
        if (byType == null) {
            val indexed = mutableMapOf<Class<*>, BaseDtoFactory<*>>()
            for (factory in factories) {
                indexed[factory.targetType()] = factory
            }
            byType = indexed
        }
        return byType!!
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(dtoClass: Class<T>): BaseDtoFactory<T> {
        val factory = index()[dtoClass] as BaseDtoFactory<T>?
            ?: throw IllegalArgumentException("No DTO factory registered for ${dtoClass.name}")
        return factory
    }
}
