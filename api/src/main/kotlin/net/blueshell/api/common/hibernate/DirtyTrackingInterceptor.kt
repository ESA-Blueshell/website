package net.blueshell.api.common.hibernate

import net.blueshell.api.model.base.DirtyAwareModel
import org.hibernate.CallbackException
import org.hibernate.Interceptor
import org.hibernate.type.Type
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Hibernate 6 compatible interceptor:
 * - implements Interceptor (EmptyInterceptor is deprecated)
 * - overrides onFlushDirty(Object id, ...)
 * 
 * 
 * Runs only for entities that extend DirtyAwareModel AND are annotated with @DirtyModel.
 */
class DirtyTrackingInterceptor : Interceptor {
    @Throws(CallbackException::class)
    override fun onFlushDirty(
        entity: Any,
        id: Any,
        currentState: Array<Any>?,
        previousState: Array<Any>?,
        propertyNames: Array<String>?,
        types: Array<Type>
    ): Boolean {
        // ultra-fast bail-outs
        if (entity !is DirtyAwareModel) return false

        val entityClass: Class<*> = entity.javaClass
        if (!isDirtyModel(entityClass)) return false
        if (previousState == null || currentState == null || propertyNames.isNullOrEmpty()) {
            return false
        }

        val trackable: MutableSet<String> = getDirtyFieldNames(entityClass)
        if (trackable.isEmpty()) return false

        val changed: MutableSet<String> = HashSet<String>()
        for (i in propertyNames.indices) {
            val prop = propertyNames[i]
            if (!trackable.contains(prop)) continue

            val prev = previousState[i]
            val curr = currentState[i]

            if (prev != curr) {
                changed.add(prop)
            }
        }

        entity.applyDirtyFields(changed)
        return false
    }

    companion object {
        private val DIRTY_MODEL_CACHE = ConcurrentHashMap<Class<*>, Boolean>()
        private val DIRTY_FIELD_NAMES_CACHE = ConcurrentHashMap<Class<*>, MutableSet<String>>()

        private fun isDirtyModel(cls: Class<*>): Boolean {
            return DIRTY_MODEL_CACHE.computeIfAbsent(cls) { c: Class<*> ->
                c.isAnnotationPresent(
                    DirtyModel::class.java
                )
            }
        }

        private fun getDirtyFieldNames(cls: Class<*>): MutableSet<String> {
            return DIRTY_FIELD_NAMES_CACHE.computeIfAbsent(cls) { cls: Class<*> -> scanDirtyFieldNames(cls) }
        }

        private fun scanDirtyFieldNames(cls: Class<*>): MutableSet<String> {
            val names: MutableSet<String> = HashSet<String>()

            // Field-level annotations
            run {
                var c = cls
                while (c != Any::class.java) {
                    for (f in c.declaredFields) {
                        if (f.isAnnotationPresent(DirtyField::class.java)) names.add(f.name)
                    }
                    c = c.superclass
                }
            }

            var c = cls
            while (c != Any::class.java) {
                for (m in c.declaredMethods) {
                    if (!m.isAnnotationPresent(DirtyField::class.java)) continue
                    val n = m.name
                    if (n.startsWith("get") && n.length > 3) names.add(decapitalize(n.substring(3)))
                    else if (n.startsWith("is") && n.length > 2) names.add(decapitalize(n.substring(2)))
                }
                c = c.superclass
            }

            return Collections.unmodifiableSet(names)
        }

        private fun decapitalize(s: String): String {
            if (s.isEmpty()) return s
            return s[0].lowercaseChar().toString() + s.substring(1)
        }
    }
}
