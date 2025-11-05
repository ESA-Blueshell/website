package net.blueshell.api.factory;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Unified entrypoint to create test fixtures (DTOs and models), delegating to registered factories.
 */
@Component
@RequiredArgsConstructor
public class UnifiedFactory {

    private final DtoFactoryRegistry dtoRegistry;
    private final FactoryRegistry modelRegistry;

    /** Indexed at startup: model product type -> invocation bundle. */
    private final Map<Class<?>, Creator<?>> modelCreators = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        indexModelFactories();
    }

    /** Minimal valid instance. */
    public <T> T basic(Class<T> type) {
        return invokeBasic(lookup(type));
    }

    /** Fully populated instance. */
    public <T> T full(Class<T> type) {
        return invokeFull(lookup(type));
    }

    /** Instance with inline tweaks (falls back to full + customizer). */
    public <T> T with(Class<T> type, Consumer<T> customizer) {
        return invokeWith(lookup(type), customizer);
    }

    /** Create N minimal instances. */
    public <T> List<T> many(int n, Class<T> type) {
        return IntStream.range(0, n)
                .mapToObj(i -> basic(type))
                .collect(Collectors.toList());
    }

    /** Create N full instances with optional customization. */
    public <T> List<T> many(int n, Class<T> type, Consumer<T> customizer) {
        return IntStream.range(0, n)
                .mapToObj(i -> with(type, customizer))
                .collect(Collectors.toList());
    }

    private void indexModelFactories() {
        for (Method accessor : FactoryRegistry.class.getDeclaredMethods()) {
            if (accessor.getParameterCount() != 0) continue;
            try {
                Object factoryBean = accessor.invoke(modelRegistry);
                if (factoryBean == null) continue;

                Class<?> beanClass = factoryBean.getClass();
                if (!beanClass.getSimpleName().endsWith("Factory")) continue;

                Method createBasic = beanClass.getMethod("createBasic");
                Method createFull = tryGet(beanClass, "createFull").orElse(createBasic);
                Method createWith = tryGet(beanClass, "createWithCustomizations", Consumer.class).orElse(null);

                Class<?> productType = createBasic.getReturnType();
                modelCreators.put(productType, new Creator<>(factoryBean, createBasic, createFull, createWith));
            } catch (ReflectiveOperationException ignored) {
                // keep indexing resilient
            }
        }
    }

    private Optional<Method> tryGet(Class<?> type, String name, Class<?>... params) {
        try {
            return Optional.of(type.getMethod(name, params));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Creator<T> lookup(Class<T> type) {
        Creator<?> creator = modelCreators.get(type);
        if (creator != null) return (Creator<T>) creator;

        try {
            BaseDtoFactory<T> dtoFactory = dtoRegistry.get(type);
            return new Creator<>(
                    dtoFactory,
                    BaseDtoFactory.class.getMethod("createBasic"),
                    BaseDtoFactory.class.getMethod("createFull"),
                    BaseDtoFactory.class.getMethod("createWithCustomizations", Consumer.class)
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No factory registered for " + type.getName());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to bind DTO factory for " + type.getName(), e);
        }
    }

    private static <T> T invokeBasic(Creator<T> c) { return c.invoke0(c.createBasic); }
    private static <T> T invokeFull(Creator<T> c) { return c.invoke0(c.createFull); }

    private static <T> T invokeWith(Creator<T> c, Consumer<T> customizer) {
        if (c.createWith == null) {
            T t = invokeFull(c);
            if (customizer != null) customizer.accept(t);
            return t;
        }
        try {
            @SuppressWarnings("unchecked")
            T t = (T) c.createWith.invoke(c.factory, customizer);
            return t;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Factory invocation failed (with customizations)", e);
        }
    }

    private record Creator<T>(Object factory, Method createBasic, Method createFull, Method createWith) {
        @SuppressWarnings("unchecked")
        T invoke0(Method m) {
            try {
                return (T) m.invoke(factory);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Factory invocation failed", e);
            }
        }
    }
}
