package net.blueshell.api.base;

import org.springframework.core.GenericTypeResolver;
import org.springframework.security.core.Authentication;

public abstract class BasePermissionEvaluator<T extends BaseModel, S extends BaseModelService<T, ?>> extends IdentityProvider {

    public final Class<T> domainType;
    protected final S service;

    public BasePermissionEvaluator(S service) {
        this.service = service;
        this.domainType = determineDomainType();
    }

    private Class<T> determineDomainType() {
        var resolvedTypes = GenericTypeResolver.resolveTypeArguments(getClass(), BasePermissionEvaluator.class);
        if (resolvedTypes == null || resolvedTypes.length < 1) {
            throw new IllegalStateException("Unable to determine domain type for " + getClass().getName());
        }
        @SuppressWarnings("unchecked")
        var castedType = (Class<T>) resolvedTypes[0];
        return castedType;
    }

    public boolean supports(Class<?> domainClass) {
        return domainType.isAssignableFrom(domainClass);
    }

    public abstract boolean hasPermission(Authentication authentication, Object targetDomainObject, String string);

    public abstract boolean hasPermissionId(Authentication authentication, Object targetId, String string);
}
