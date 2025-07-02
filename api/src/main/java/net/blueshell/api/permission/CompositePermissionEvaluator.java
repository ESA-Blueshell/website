package net.blueshell.api.permission;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BasePermissionEvaluator;
import net.blueshell.api.base.IdentityProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.io.Serializable;
import java.util.List;

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
        log.info("hasPermission(Authentication auth, Object target, Object perm)");
        if (target == null || perm == null) return false;
        Class<?> domainClass = ClassUtils.getUserClass(target.getClass());
        System.out.println("domainclass: " + domainClass.getName());
        return evaluators.stream()
                .filter(e -> e.supports(domainClass))
                .findFirst()
                .map(e -> e.hasPermission(auth, target, perm.toString()))
                .orElse(false);
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object perm) {
        log.info("hasPermission(Authentication auth, Serializable targetId, String targetType, Object perm)");
        log.info("Auth: " + auth);
        log.info("targetId: " + targetId);
        log.info("targetType: " + targetType);
        log.info("perm: " + perm);
        if (targetId == null || targetType == null || perm == null) return false;
        var temp = evaluators.stream()
                .filter(e -> {
                    Class<?> dt = e.domainType;
                    return dt.getSimpleName().equals(targetType)
                            || dt.getName().equals(targetType);
                }).findFirst();

        log.info("Found: " + temp);

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
