package net.blueshell.api.security.permission

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.util.ClassUtils
import java.io.Serializable
import java.util.function.Function

@Component
class CompositePermissionEvaluator @Autowired constructor(
    private val evaluators: MutableList<BasePermissionEvaluator<*, *, *>>
) :
    PermissionEvaluator {
    override fun hasPermission(authentication: Authentication, targetDomainObject: Any, permission: Any): Boolean {
        val domainClass = ClassUtils.getUserClass(targetDomainObject.javaClass)
        return evaluators.stream()
            .filter { evaluator -> evaluator.supports(domainClass) }
            .findFirst()
            .map(Function { evaluator ->
                evaluator.hasPermission(
                    authentication,
                    targetDomainObject,
                    permission.toString()
                )
            }).orElse(false)
    }

    override fun hasPermission(
        authentication: Authentication,
        targetId: Serializable,
        targetType: String,
        permission: Any
    ): Boolean {
        val evaluator = evaluators.stream()
            .filter { evaluator ->
                val dt: Class<*> = evaluator.domainType
                dt.simpleName == targetType
                        || dt.name == targetType
            }
            .findFirst()
            .orElse(null)
            ?: return false

        if (targetId.toString() == NO_TARGET_SENTINEL) {
            return evaluator.hasPermission(authentication, null, permission.toString())
        }

        return evaluator.hasPermissionId(authentication, targetId, permission.toString())
    }

    companion object {
        private const val NO_TARGET_SENTINEL = "__NO_TARGET__"
    }
}
