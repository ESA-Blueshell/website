package net.blueshell.api.common.event.jpa;

import jakarta.persistence.*;
import lombok.Setter;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Pure JPA listener that snapshots entities on load/persist and diffs at update time.
 * This mirrors Hibernate's own snapshot-based dirty checking model. :contentReference[oaicite:2]{index=2}
 */
public class JpaListener {

    /**
     * Keep lightweight snapshots per managed entity without leaking memory.
     */
    private static final Map<Object, Map<String, Object>> SNAPSHOTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    @Setter
    private static ApplicationEventPublisher publisher;

    // -------- Lifecycle callbacks --------

    private static Map<String, Object> snapshot(Object entity) {
        Map<String, Object> state = new LinkedHashMap<>();
        for (Field f : allFields(entity.getClass())) {
            if (!isIncluded(f)) continue;
            try {
                f.setAccessible(true);
                Object value = f.get(entity);
                // For @ManyToOne, reduce to ID if available to avoid proxy churn
                if (f.isAnnotationPresent(ManyToOne.class) || f.isAnnotationPresent(OneToOne.class)) {
                    state.put(f.getName(), extractId(value));
                } else if (f.isAnnotationPresent(OneToMany.class) || f.isAnnotationPresent(ManyToMany.class)) {
                    // collections can be huge; store size only (adjust if you want deep diffs)
                    state.put(f.getName(), (value instanceof Collection<?> c) ? c.size() : null);
                } else {
                    state.put(f.getName(), value);
                }
            } catch (IllegalAccessException ignored) { /* no-op */ }
        }
        return state;
    }

    private static Map<String, ValueChange> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, ValueChange> out = new LinkedHashMap<>();
        Set<String> keys = new TreeSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String k : keys) {
            Object b = before.get(k);
            Object a = after.get(k);
            if (!Objects.equals(b, a)) {
                out.put(k, new ValueChange(b, a));
            }
        }
        return out;
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }

    private static boolean isIncluded(Field f) {
        int m = f.getModifiers();
        if (Modifier.isStatic(m) || Modifier.isTransient(m)) return false;
        if (f.isAnnotationPresent(Transient.class)) return false;
        return !f.getName().equals("serialVersionUID");
    }

    private static Object extractId(Object maybeEntity) {
        if (maybeEntity == null) return null;
        try {
            var m = maybeEntity.getClass().getMethod("getId");
            m.setAccessible(true);
            return m.invoke(maybeEntity);
        } catch (Exception e) {
            return maybeEntity.toString();
        }
    }

    @PostLoad
    public void postLoad(Object entity) {
        SNAPSHOTS.put(entity, snapshot(entity));
    }

    @PrePersist
    public void prePersist(Object entity) {
        if (publisher == null) return;
        Map<String, Object> before = Collections.emptyMap();
        Map<String, Object> after = snapshot(entity);
        Map<String, ValueChange> changes = diff(before, after);
        publisher.publishEvent(new PrePersistEvent<>(entity, before, after, changes));
    }

    // -------- Snapshot & diff utilities --------

    @PostPersist
    public void postPersist(Object entity) {
        if (publisher == null) return;
        Map<String, Object> before = Collections.emptyMap();
        Map<String, Object> after = snapshot(entity);
        Map<String, ValueChange> changes = diff(before, after);
        SNAPSHOTS.put(entity, after);
        publisher.publishEvent(new PostPersistEvent<>(entity, before, after, changes));
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        if (publisher == null) return;
        Map<String, Object> before = SNAPSHOTS.getOrDefault(entity, snapshot(entity));
        Map<String, Object> after = snapshot(entity);
        Map<String, ValueChange> changes = diff(before, after);
        publisher.publishEvent(new PreUpdateEvent<>(entity, before, after, changes));
    }

    @PostUpdate
    public void postUpdate(Object entity) {
        if (publisher == null) return;
        Map<String, Object> before = SNAPSHOTS.getOrDefault(entity, snapshot(entity));
        Map<String, Object> after = snapshot(entity);
        Map<String, ValueChange> changes = diff(before, after);
        SNAPSHOTS.put(entity, after);
        publisher.publishEvent(new PostUpdateEvent<>(entity, before, after, changes));
    }

    @PreRemove
    public void preRemove(Object entity) {
        if (publisher == null) return;
        Map<String, Object> before = SNAPSHOTS.getOrDefault(entity, snapshot(entity));
        Map<String, Object> after = Collections.emptyMap();
        Map<String, ValueChange> changes = diff(before, after);
        publisher.publishEvent(new PreRemoveEvent<>(entity, before, after, changes));
    }

    @PostRemove
    public void postRemove(Object entity) {
        if (publisher == null) return;
        Map<String, Object> before = SNAPSHOTS.getOrDefault(entity, snapshot(entity));
        Map<String, Object> after = Collections.emptyMap();
        Map<String, ValueChange> changes = diff(before, after);
        SNAPSHOTS.remove(entity);
        publisher.publishEvent(new PostRemoveEvent<>(entity, before, after, changes));
    }
}
