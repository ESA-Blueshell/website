package net.blueshell.api.common.event.jpa;

import lombok.Getter;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

public class PostUpdateEvent<T> implements ResolvableTypeProvider {
    @Getter
    private T source;

    public PostUpdateEvent(T source) {
        this.source = source;
    }

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClassWithGenerics(
                getClass(), ResolvableType.forInstance(getSource())
        );
    }
}