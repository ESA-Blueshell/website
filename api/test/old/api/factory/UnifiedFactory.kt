package net.blueshell.api.factory

import jakarta.annotation.PostConstruct
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.IntStream

/**
 * Unified entrypoint to create test fixtures (DTOs and models), delegating to registered factories.
 */
@Component
class UnifiedFactory(
    private val dtoRegistry: DtoFactoryRegistry,
    private val modelRegistry: FactoryRegistry
) {

    /** Indexed at startup: model product type -> invocation bundle. */
    private val modelCreators: MutableMap<Class<*>, Creator<*>> = ConcurrentHashMap()

    @PostConstruct
    fun init() {
        indexModelFactories()
    }

    /** Minimal valid instance. */
    fun <T> basic(type: Class<T>): T = invokeBasic(lookup(type))

    /** Fully populated instance. */
    fun <T> full(type: Class<T>): T = invokeFull(lookup(type))

    /** Instance with inline tweaks (falls back to full + customizer). */
    fun <T> with(type: Class<T>, customizer: Consumer<T>): T = invokeWith(lookup(type), customizer)

    /** Create N minimal instances. */
    fun <T> many(n: Int, type: Class<T>): List<T> {
        return IntStream.range(0, n)
            .mapToObj { basic(type) }
            .collect(Collectors.toList())
    }

    /** Create N full instances with optional customization. */
    fun <T> many(n: Int, type: Class<T>, customizer: Consumer<T>): List<T> {
        return IntStream.range(0, n)
            .mapToObj { with(type, customizer) }
            .collect(Collectors.toList())
    }

    private fun indexModelFactories() {
        for (accessor in FactoryRegistry::class.java.declaredMethods) {
            if (accessor.parameterCount != 0) continue
            try {
                val factoryBean = accessor.invoke(modelRegistry) ?: continue
                val beanClass = factoryBean.javaClass
                if (!beanClass.simpleName.endsWith("Factory")) continue

                val createBasic = beanClass.getMethod("createBasic")
                val createFull = tryGet(beanClass, "createFull") ?: createBasic
                val createWith = tryGet(beanClass, "createWithCustomizations", Consumer::class.java)

                val productType = createBasic.returnType
                modelCreators[productType] = Creator<Any>(factoryBean, createBasic, createFull, createWith)
            } catch (_: ReflectiveOperationException) {
                // keep indexing resilient
            }
        }
    }

    private fun tryGet(type: Class<*>, name: String, vararg params: Class<*>): Method? {
        return try {
            type.getMethod(name, *params)
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> lookup(type: Class<T>): Creator<T> {
        val creator = modelCreators[type]
        if (creator != null) return creator as Creator<T>

        return try {
            val dtoFactory = dtoRegistry.get(type)
            Creator<T>(
                dtoFactory,
                BaseDtoFactory::class.java.getMethod("createBasic"),
                BaseDtoFactory::class.java.getMethod("createFull"),
                BaseDtoFactory::class.java.getMethod("createWithCustomizations", Consumer::class.java)
            )
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("No factory registered for ${type.name}")
        } catch (ex: ReflectiveOperationException) {
            throw RuntimeException("Failed to bind DTO factory for ${type.name}", ex)
        }
    }

    private fun <T> invokeBasic(c: Creator<T>): T = c.invoke0(c.createBasic)

    private fun <T> invokeFull(c: Creator<T>): T = c.invoke0(c.createFull)

    private fun <T> invokeWith(c: Creator<T>, customizer: Consumer<T>): T {
        if (c.createWith == null) {
            val t = invokeFull(c)
            customizer.accept(t)
            return t
        }
        return try {
            @Suppress("UNCHECKED_CAST")
            c.createWith.invoke(c.factory, customizer) as T
        } catch (ex: ReflectiveOperationException) {
            throw RuntimeException("Factory invocation failed (with customizations)", ex)
        }
    }

    private data class Creator<T>(
        val factory: Any,
        val createBasic: Method,
        val createFull: Method,
        val createWith: Method?
    ) {
        @Suppress("UNCHECKED_CAST")
        fun invoke0(m: Method): T {
            return try {
                m.invoke(factory) as T
            } catch (ex: ReflectiveOperationException) {
                throw RuntimeException("Factory invocation failed", ex)
            }
        }
    }
}
