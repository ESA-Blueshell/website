package net.blueshell.api.testutil

import java.lang.reflect.Field

object ModelTestUtils {
    fun setId(model: Any, id: Long) {
        val field = findField(model.javaClass, "id")
        field.isAccessible = true
        try {
            field.set(model, id)
        } catch (ex: IllegalAccessException) {
            throw RuntimeException("Failed to set id on ${model.javaClass.name}", ex)
        }
    }

    fun setField(target: Any, fieldName: String, value: Any?) {
        val field = findField(target.javaClass, fieldName)
        field.isAccessible = true
        try {
            field.set(target, value)
        } catch (ex: IllegalAccessException) {
            throw RuntimeException("Failed to set field $fieldName on ${target.javaClass.name}", ex)
        }
    }

    private fun findField(type: Class<*>, name: String): Field {
        var current: Class<*>? = type
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw IllegalArgumentException("Field not found: $name on ${type.name}")
    }
}
