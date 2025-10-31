package net.blueshell.api.common.event.jpa;

public class PostRemoveEvent<T> extends BaseJpaEvent<T> {
    public PostRemoveEvent(T source) {
        super(source);
    }

    public PostRemoveEvent(T source,
                           java.util.Map<String, Object> beforeState,
                           java.util.Map<String, Object> afterState,
                           java.util.Map<String, ValueChange> changes) {
        super(source, beforeState, afterState, changes);
    }
}
