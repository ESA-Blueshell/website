package net.blueshell.api.common.event.jpa;

public class PrePersistEvent<T> extends BaseJpaEvent<T> {
    public PrePersistEvent(T source) {
        super(source);
    }

    public PrePersistEvent(T source,
                           java.util.Map<String, Object> beforeState,
                           java.util.Map<String, Object> afterState,
                           java.util.Map<String, ValueChange> changes) {
        super(source, beforeState, afterState, changes);
    }
}
