package net.blueshell.api.common.event.jpa;

import lombok.Getter;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

abstract class BaseJpaEvent<T> implements ResolvableTypeProvider {
    @Getter
    private final T source;
    @Getter
    private final Map<String, Object> beforeState;
    @Getter
    private final Map<String, Object> afterState;
    @Getter
    private final Map<String, ValueChange> changes;
    @Getter
    private final Set<String> changedProperties;

    protected BaseJpaEvent(T source) {
        this(source, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    protected BaseJpaEvent(T source,
                           Map<String, Object> beforeState,
                           Map<String, Object> afterState,
                           Map<String, ValueChange> changes) {
        this.source = source;
        this.beforeState = Collections.unmodifiableMap(new LinkedHashMap<>(beforeState));
        this.afterState = Collections.unmodifiableMap(new LinkedHashMap<>(afterState));
        this.changes = Collections.unmodifiableMap(new LinkedHashMap<>(changes));
        this.changedProperties = Collections.unmodifiableSet(this.changes.keySet());
    }

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClassWithGenerics(getClass(), ResolvableType.forInstance(getSource()));
    }
}
