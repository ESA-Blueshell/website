package net.blueshell.api.common.event.jpa;

public class PreUpdateEvent<T> extends BaseJpaEvent<T> {
    public PreUpdateEvent(T source) {
        super(source);
    }

    public PreUpdateEvent(T source,
                          java.util.Map<String, Object> beforeState,
                          java.util.Map<String, Object> afterState,
                          java.util.Map<String, ValueChange> changes) {
        super(source, beforeState, afterState, changes);
    }
}
