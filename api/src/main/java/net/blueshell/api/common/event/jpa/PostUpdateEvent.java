package net.blueshell.api.common.event.jpa;

public class PostUpdateEvent<T> extends BaseJpaEvent<T> {
    public PostUpdateEvent(T source) {
        super(source);
    }

    public PostUpdateEvent(T source, java.util.Map<String, Object> beforeState, java.util.Map<String, Object> afterState, java.util.Map<String, ValueChange> changes) {
        super(source, beforeState, afterState, changes);
    }
}
