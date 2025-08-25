package net.blueshell.api.common.event;

import lombok.Getter;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

public class PostDeleteEvent<T> implements ResolvableTypeProvider {
    @Getter
    private T source;

    public PostDeleteEvent(T source) {
        this.source = source;
    }

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClassWithGenerics(
                getClass(), ResolvableType.forInstance(getSource())
        );
    }
}