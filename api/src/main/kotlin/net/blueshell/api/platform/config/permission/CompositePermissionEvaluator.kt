package net.blueshell.api.platform.config.permission

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.util.ClassUtils
import java.io.Serializable
import java.util.function.Function

@Component
class CompositePermissionEvaluator @Autowired constructor(private val evaluators: MutableList<BasePermissionEvaluator<*, *, *>?>) :
    PermissionEvaluator {
    override fun hasPermission(auth: Authentication?, target: Any?, perm: Any?): Boolean {
        if (target == null || perm == null) return false
        val domainClass = ClassUtils.getUserClass(target.javaClass)
        return evaluators.stream()
            .filter { e: BasePermissionEvaluator<*, *, *>? -> e!!.supports(domainClass) }
            .findFirst()
            .map(Function { e: BasePermissionEvaluator<*, *, *>? ->
                e!!.hasPermission(
                    auth,
                    target,
                    perm.toString()
                )
            }).orElse(false)
    }

    override fun hasPermission(
        auth: Authentication?,
        targetId: Serializable?,
        targetType: String?,
        perm: Any?
    ): Boolean {
        if (targetId == null || targetType == null || perm == null) return false

        return evaluators.stream()
            .filter { e: BasePermissionEvaluator<*, *, *>? ->
                val dt: Class<*> = e!!.domainType
                dt.simpleName == targetType
                        || dt.name == targetType
            }
            .findFirst()
            .map(Function { e: BasePermissionEvaluator<*, *, *>? ->
                e!!.hasPermissionId(
                    auth,
                    targetId,
                    perm.toString()
                )
            })
            .orElse(false)
    }
}
