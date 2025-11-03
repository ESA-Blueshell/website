package net.blueshell.api.common.event.jpa;

import lombok.Getter;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

public class PreRemoveEvent<T> implements ResolvableTypeProvider {
    @Getter
    private T source;

    public PreRemoveEvent(T source) {
        this.source = source;
    }

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClassWithGenerics(
                getClass(), ResolvableType.forInstance(getSource())
        );
    }
}