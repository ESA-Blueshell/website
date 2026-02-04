package net.blueshell.api.testutil;

import java.lang.reflect.Field;

public final class ModelTestUtils {
    private ModelTestUtils() {
    }

    public static void setId(Object model, Long id) {
        Field field = findField(model.getClass(), "id");
        field.setAccessible(true);
        try {
            field.set(model, id);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set id on " + model.getClass().getName(), e);
        }
    }

    public static void setField(Object target, String fieldName, Object value) {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field " + fieldName + " on " + target.getClass().getName(), e);
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("Field not found: " + name + " on " + type.getName());
    }
}
