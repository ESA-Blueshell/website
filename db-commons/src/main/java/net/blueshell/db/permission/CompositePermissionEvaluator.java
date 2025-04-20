package net.blueshell.db.permission;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.common.identity.IdentityProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class CompositePermissionEvaluator extends IdentityProvider implements PermissionEvaluator {

    private final List<BasePermissionEvaluator<?, ?, ?>> evaluators;

    @Autowired
    public CompositePermissionEvaluator(List<BasePermissionEvaluator<?, ?, ?>> evaluators) {
        this.evaluators = evaluators;
    }

    @Override
    public boolean hasPermission(Authentication auth, Object target, Object perm) {
        log.info("HasPermission for: {}", target);
        log.info("With principal: {}", getPrincipal());
        if (target == null || perm == null) return false;
        Class<?> domainClass = ClassUtils.getUserClass(target.getClass());
        return evaluators.stream()
                .filter(e -> e.supports(domainClass))
                .findFirst()
                .map(e -> e.hasPermission(auth, target, perm.toString()))
                .orElse(false);
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object perm) {
        log.info("HasPermission id for: {}", targetType);
        log.info("With principal: {}", getPrincipal());
        if (targetId == null || targetType == null || perm == null) return false;

        return evaluators.stream()
                .filter(e -> {
                    Class<?> dt = e.domainType;
                    return dt.getSimpleName().equals(targetType)
                            || dt.getName().equals(targetType);
                })
                .findFirst()
                .map(e -> e.hasPermissionId(auth, targetId, perm.toString()))
                .orElse(false);
    }
}
