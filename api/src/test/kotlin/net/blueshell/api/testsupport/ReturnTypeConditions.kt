package net.blueshell.api.testsupport

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaType
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent

/**
 * ArchUnit conditions for matching method return types (incl. generic erasures).
 */
object ReturnTypeConditions {

    /** Passes if the return type matches the predicate. */
    fun haveReturnType(predicate: DescribedPredicate<in JavaType>): ArchCondition<JavaMethod> {
        val desc = "have return type ${predicate.description}"
        return object : ArchCondition<JavaMethod>(desc) {
            override fun check(method: JavaMethod, events: ConditionEvents) {
                val actual = method.returnType
                val ok = predicate.test(actual)
                if (!ok) {
                    val message =
                        "Method <${method.fullName}> has return type <${actual.name}> which does not ${predicate.description}"
                    events.add(SimpleConditionEvent.violated(method, message))
                }
            }
        }
    }

    /** Passes if the return type does NOT match the predicate. */
    fun notHaveReturnType(predicate: DescribedPredicate<in JavaType>): ArchCondition<JavaMethod> {
        val desc = "not have return type ${predicate.description}"
        return object : ArchCondition<JavaMethod>(desc) {
            override fun check(method: JavaMethod, events: ConditionEvents) {
                val actual = method.returnType
                val matches = predicate.test(actual)
                if (matches) {
                    val message =
                        "Method <${method.fullName}> has return type <${actual.name}> which unexpectedly ${predicate.description}"
                    events.add(SimpleConditionEvent.violated(method, message))
                }
            }
        }
    }
}
