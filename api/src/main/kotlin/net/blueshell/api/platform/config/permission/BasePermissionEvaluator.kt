package net.blueshell.api.platform.config.permission

import net.blueshell.api.feature.auth.security.IdentityProvider
import net.blueshell.api.shared.model.Identifiable
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.core.GenericTypeResolver
import org.springframework.security.core.Authentication

abstract class BasePermissionEvaluator<T : Identifiable<ID>, ID, S : BaseModelService<T, ID, *>>(protected val service: S) :
    IdentityProvider() {
    val domainType: Class<T>

    init {
        this.domainType = determineDomainType()
    }

    private fun determineDomainType(): Class<T> {
        val resolvedTypes = GenericTypeResolver.resolveTypeArguments(javaClass, BasePermissionEvaluator::class.java)
        check(!(resolvedTypes == null || resolvedTypes.size < 1)) { "Unable to determine domain type for " + javaClass.name }
        return resolvedTypes[0] as Class<T>
    }

    fun supports(domainClass: Class<*>): Boolean {
        return domainType.isAssignableFrom(domainClass)
    }

    abstract fun hasPermission(authentication: Authentication?, targetDomainObject: Any?, string: String?): Boolean

    abstract fun hasPermissionId(authentication: Authentication?, targetId: Any?, string: String?): Boolean
}