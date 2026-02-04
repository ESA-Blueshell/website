package net.blueshell.api.testsupport

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaParameterizedType
import com.tngtech.archunit.core.domain.JavaType
import com.tngtech.archunit.core.domain.JavaWildcardType

/**
 * Helpers for ArchUnit predicates involving generic return types.
 */
object GenericsPredicates {

    fun assignableToGeneric(raw: Class<*>, vararg typeArgs: Class<*>): DescribedPredicate<JavaType> =
        assignableToGeneric(raw = raw, allowRaw = false, typeArgs = typeArgs)

    fun assignableToGeneric(
        raw: Class<*>,
        allowRaw: Boolean,
        vararg typeArgs: Class<*>
    ): DescribedPredicate<JavaType> {
        val description = describe(raw, typeArgs.toList())
        return DescribedPredicate.describe("assignable to $description") { type ->
            val erasure: JavaClass = type.toErasure()
            if (!erasure.isAssignableTo(raw)) return@describe false

            val parameterized = type as? JavaParameterizedType ?: return@describe allowRaw
            val actualArgs = parameterized.actualTypeArguments
            if (actualArgs.size != typeArgs.size) return@describe false

            actualArgs.zip(typeArgs.asList()).all { (actual, expected) ->
                typeArgMatches(actual, expected)
            }
        }
    }

    private fun typeArgMatches(actual: JavaType, expected: Class<*>): Boolean {
        val wildcard = actual as? JavaWildcardType
        if (wildcard != null) {
            val uppers = wildcard.upperBounds
            if (uppers.isEmpty()) return expected == Any::class.java
            return uppers.any { it.toErasure().isAssignableTo(expected) }
        }
        return actual.toErasure().isAssignableTo(expected)
    }

    private fun describe(raw: Class<*>, typeArgs: List<Class<*>>): String {
        if (typeArgs.isEmpty()) return raw.simpleName
        val args = typeArgs.joinToString(", ") { it.simpleName }
        return "${raw.simpleName}<$args>"
    }
}
