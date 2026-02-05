package net.blueshell.api.architecture.support

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaParameterizedType
import com.tngtech.archunit.core.domain.JavaType
import com.tngtech.archunit.core.domain.JavaWildcardType
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import kotlin.collections.forEach

/**
 * Conditions that inspect method signatures (return + parameters), including generic arguments.
 *
 * Example: ResponseEntity<List<UserEntity>> will "reference" UserEntity.
 */
object SignatureConditions {

    fun notReferenceTypes(predicate: DescribedPredicate<in JavaClass>): ArchCondition<JavaMethod> {
        val desc = "not reference types in signature that ${predicate.description}"
        return object : ArchCondition<JavaMethod>(desc) {
            override fun check(method: JavaMethod, events: ConditionEvents) {
                val referenced = referencedErasures(method)
                val offenders = referenced.filter(predicate::test).distinctBy { it.fullName }

                if (offenders.isNotEmpty()) {
                    val msg = buildString {
                        append("Method <${method.fullName}> references forbidden types in its signature: ")
                        append(offenders.joinToString { it.fullName })
                    }
                    events.add(SimpleConditionEvent.violated(method, msg))
                }
            }
        }
    }

    private fun referencedErasures(method: JavaMethod): Set<JavaClass> {
        val out = linkedSetOf<JavaClass>()

        collectErasures(method.returnType, mutableSetOf(), out)

        val genericParams = method.safeGenericParameterTypes()
        if (genericParams.isNotEmpty()) {
            genericParams.forEach { collectErasures(it, mutableSetOf(), out) }
        } else {
            method.safeRawParameterTypes().forEach(out::add)
        }

        return out
    }

    /**
     * Try getParameterTypes() (generic types); fallback if your ArchUnit version lacks it.
     */
    private fun JavaMethod.safeGenericParameterTypes(): List<JavaType> =
        runCatching {
            val m = this.javaClass.getMethod("getParameterTypes")
            @Suppress("UNCHECKED_CAST")
            m.invoke(this) as List<JavaType>
        }.getOrDefault(emptyList())

    private fun JavaMethod.safeRawParameterTypes(): List<JavaClass> =
        runCatching {
            val m = this.javaClass.getMethod("getRawParameterTypes")
            @Suppress("UNCHECKED_CAST")
            m.invoke(this) as List<JavaClass>
        }.getOrDefault(emptyList())

    private fun collectErasures(type: JavaType, visited: MutableSet<JavaType>, out: MutableSet<JavaClass>) {
        if (!visited.add(type)) return

        out.add(type.toErasure())

        when (type) {
            is JavaParameterizedType -> type.actualTypeArguments.forEach { collectErasures(it, visited, out) }
            is JavaWildcardType -> {
                type.upperBounds.forEach { collectErasures(it, visited, out) }
                lowerBoundsSafely(type).forEach { collectErasures(it, visited, out) }
            }
        }
    }

    private fun lowerBoundsSafely(wildcard: JavaWildcardType): List<JavaType> =
        runCatching {
            val m = wildcard.javaClass.getMethod("getLowerBounds")
            @Suppress("UNCHECKED_CAST")
            m.invoke(wildcard) as List<JavaType>
        }.getOrDefault(emptyList())
}