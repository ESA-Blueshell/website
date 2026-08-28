package net.blueshell.api.contribution.domain

import java.time.Instant

/**
 * The result mappers read `id` and the audit timestamps, which the framework
 * assigns and which are `lateinit` on the entity, so a unit test has to seed them
 * reflectively before mapping.
 */
internal fun <T : Any> T.seeded(id: Long = 1L): T = apply {
    setField(this, "id", id)
    seededTimestamps()
}

/** Ids differ in shape between entities — composite for contributions — so they are set by the caller. */
internal fun <T : Any> T.seededTimestamps(): T = apply {
    setField(this, "createdAt", Instant.parse("2024-01-01T00:00:00Z"))
    setField(this, "updatedAt", Instant.parse("2024-01-01T00:00:00Z"))
}

private fun setField(target: Any, name: String, value: Any?) {
    var current: Class<*>? = target::class.java
    while (current != null) {
        try {
            val field = current.getDeclaredField(name)
            field.isAccessible = true
            field.set(target, value)
            return
        } catch (_: NoSuchFieldException) {
            current = current.superclass
        }
    }
}
