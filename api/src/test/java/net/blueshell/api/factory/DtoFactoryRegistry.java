package net.blueshell.api.factory;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of DTO factories by their target type.
 */
@Component
@RequiredArgsConstructor
public class DtoFactoryRegistry {

    private final List<BaseDtoFactory<?>> factories;
    private Map<Class<?>, BaseDtoFactory<?>> byType;

    private Map<Class<?>, BaseDtoFactory<?>> index() {
        if (byType == null) {
            byType = new HashMap<>();
            for (BaseDtoFactory<?> f : factories) {
                byType.put(f.targetType(), f);
            }
        }
        return byType;
    }

    @SuppressWarnings("unchecked")
    public <T> BaseDtoFactory<T> get(Class<T> dtoClass) {
        BaseDtoFactory<T> f = (BaseDtoFactory<T>) index().get(dtoClass);
        if (f == null) {
            throw new IllegalArgumentException("No DTO factory registered for " + dtoClass.getName());
        }
        return f;
    }
}
