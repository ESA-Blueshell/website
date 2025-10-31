package net.blueshell.api.common.event.jpa;

public class PreRemoveEvent<T> extends BaseJpaEvent<T> {
    public PreRemoveEvent(T source) {
        super(source);
    }

    public PreRemoveEvent(T source,
                          java.util.Map<String, Object> beforeState,
                          java.util.Map<String, Object> afterState,
                          java.util.Map<String, ValueChange> changes) {
        super(source, beforeState, afterState, changes);
    }
}
