package net.blueshell.api.common.hibernate;

import net.blueshell.api.base.DirtyAwareModel;
import org.hibernate.CallbackException;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hibernate 6 compatible interceptor:
 * - implements Interceptor (EmptyInterceptor is deprecated)
 * - overrides onFlushDirty(Object id, ...)
 * <p>
 * Runs only for entities that extend DirtyAwareModel AND are annotated with @DirtyModel.
 */
public class DirtyTrackingInterceptor implements Interceptor {

    private static final ConcurrentHashMap<Class<?>, Boolean> DIRTY_MODEL_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Set<String>> DIRTY_FIELD_NAMES_CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean onFlushDirty(
            Object entity,
            Object id,
            Object[] currentState,
            Object[] previousState,
            String[] propertyNames,
            Type[] types
    ) throws CallbackException {
        // ultra-fast bail-outs
        if (!(entity instanceof DirtyAwareModel)) return false;

        Class<?> entityClass = entity.getClass();
        if (!isDirtyModel(entityClass)) return false;
        if (previousState == null || currentState == null || propertyNames == null || propertyNames.length == 0) {
            return false;
        }

        Set<String> trackable = getDirtyFieldNames(entityClass);
        if (trackable.isEmpty()) return false;

        Set<String> changed = null;
        for (int i = 0; i < propertyNames.length; i++) {
            String prop = propertyNames[i];
            if (!trackable.contains(prop)) continue;

            Object prev = previousState[i];
            Object curr = currentState[i];

            if (!Objects.equals(prev, curr)) {
                if (changed == null) changed = new LinkedHashSet<>();
                changed.add(prop);
            }
        }

        ((DirtyAwareModel) entity).__applyDirtyFields(changed == null ? Collections.emptySet() : changed);
        return false;
    }

    private static boolean isDirtyModel(Class<?> cls) {
        return DIRTY_MODEL_CACHE.computeIfAbsent(cls, c -> c.isAnnotationPresent(DirtyModel.class));
    }

    private static Set<String> getDirtyFieldNames(Class<?> cls) {
        return DIRTY_FIELD_NAMES_CACHE.computeIfAbsent(cls, DirtyTrackingInterceptor::scanDirtyFieldNames);
    }

    private static Set<String> scanDirtyFieldNames(Class<?> cls) {
        Set<String> names = new HashSet<>();

        // Field-level annotations
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(DirtyField.class)) names.add(f.getName());
            }
        }

        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(DirtyField.class)) continue;
                String n = m.getName();
                if (n.startsWith("get") && n.length() > 3) names.add(decapitalize(n.substring(3)));
                else if (n.startsWith("is") && n.length() > 2) names.add(decapitalize(n.substring(2)));
            }
        }

        return Collections.unmodifiableSet(names);
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
