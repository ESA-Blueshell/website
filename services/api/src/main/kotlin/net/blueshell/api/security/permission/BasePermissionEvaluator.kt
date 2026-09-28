package net.blueshell.api.security.permission

import net.blueshell.api.shared.model.Identifiable
import org.springframework.core.GenericTypeResolver
import org.springframework.security.core.Authentication

/** Answers `hasPermission` for one entity type; a subclass reads whatever it needs to. */
abstract class BasePermissionEvaluator<T : Identifiable<ID>, ID : Any> {
    val domainType: Class<T>

    init {
        this.domainType = determineDomainType()
    }

    private fun determineDomainType(): Class<T> {
        val resolvedTypes = GenericTypeResolver.resolveTypeArguments(javaClass, BasePermissionEvaluator::class.java)
        check(!(resolvedTypes == null || resolvedTypes.size < 1)) { "Unable to determine domain type for " + javaClass.name }
        @Suppress("UNCHECKED_CAST")
        return resolvedTypes[0] as Class<T>
    }

    fun supports(domainClass: Class<*>): Boolean = domainType.isAssignableFrom(domainClass)

    abstract fun hasPermission(
        authentication: Authentication?,
        entity: Any?,
        permission: String?,
    ): Boolean

    abstract fun hasPermissionId(
        authentication: Authentication?,
        id: Any?,
        permission: String?,
    ): Boolean
}
