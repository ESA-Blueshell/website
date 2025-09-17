package net.blueshell.api.controller.interceptor;


import net.blueshell.api.base.BaseModel;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

@ControllerAdvice
public class ResponseInterceptor implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(@NotNull MethodParameter returnType, @NotNull Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, @NotNull MethodParameter returnType, @NotNull MediaType selectedContentType, @NotNull Class selectedConverterType, @NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response) {
        if (body != null) {
            // Check if the returned object is an instance of a forbidden model
            if (body instanceof BaseModel) {
                throw new IllegalStateException("Direct return of model objects is forbidden. Use a DTO instead.");
            }
            // Handle collections or arrays of models if needed
            if (body instanceof Iterable) {
                for (Object item : (Iterable<?>) body) {
                    if ((item instanceof BaseModel)) {
                        throw new IllegalStateException("Direct return of model objects inside collections is forbidden. Use a DTO instead.");
                    }
                }
            }
            // TODO: Check if password in response body.
        }
        return body;
    }

    private boolean containsPassword(Object body) {
        if (body == null) return false;

        if (body.getClass().isArray()) {
            Object[] arr = (Object[]) body;
            for (Object o : arr) {
                if (containsPassword(o)) return true;
            }
            return false;
        }

        if (body instanceof Collection<?> collection) {
            for (Object o : collection) {
                if (containsPassword(o)) return true;
            }
            return false;
        }

        if (body instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() instanceof String key && "password" .equals(key)) {
                    if (hasValue(e.getValue())) return true;
                }
                if (containsPassword(e.getValue())) return true;
            }
            return false;
        }

        return hasPasswordValue(body);
    }

    private boolean hasPasswordValue(Object obj) {
        if (obj == null) return false;

        // Try getter: getPassword()
        try {
            Method m = obj.getClass().getMethod("getPassword");
            if (m.getParameterCount() == 0) {
                Object value = m.invoke(obj);
                if (hasValue(value)) return true;
            }
        } catch (NoSuchMethodException ignored) {
            // no-op
        } catch (Exception reflectionError) {
            // If reflection fails for other reasons, be conservative and do not block
        }

        // Try field named "password" up the class hierarchy
        Field f = findFieldRecursive(obj.getClass(), "password");
        if (f != null) {
            try {
                f.setAccessible(true);
                Object value = f.get(obj);
                if (hasValue(value)) return true;
            } catch (Exception ignored) {
                // If we can't access, skip
            }
        }

        return false;
    }

    private Field findFieldRecursive(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private boolean hasValue(Object value) {
        return switch (value) {
            case null -> false;
            case String s -> !s.isBlank();
            case char[] chars -> chars.length > 0;
            default ->
                true;
        };
    }
}