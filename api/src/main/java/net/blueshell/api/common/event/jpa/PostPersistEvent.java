package net.blueshell.api.common.event.jpa;

public class PostPersistEvent<T> extends BaseJpaEvent<T> {
    public PostPersistEvent(T source) {
        super(source);
    }

    public PostPersistEvent(T source,
                            java.util.Map<String, Object> beforeState,
                            java.util.Map<String, Object> afterState,
                            java.util.Map<String, ValueChange> changes) {
        super(source, beforeState, afterState, changes);
    }
}
